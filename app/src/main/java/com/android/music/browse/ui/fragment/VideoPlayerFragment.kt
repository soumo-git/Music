package com.android.music.browse.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.R
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.browse.ui.adapter.YouTubeVideoAdapter
import com.android.music.browse.ui.viewmodel.BrowseViewModel
import com.android.music.databinding.FragmentVideoPlayerBinding
import com.android.music.download.manager.DownloadStateManager
import com.android.music.download.ui.viewmodel.DownloadsViewModel
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VideoPlayerFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentVideoPlayerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowseViewModel by activityViewModels()
    private val downloadsViewModel: DownloadsViewModel by activityViewModels()
    private lateinit var relatedAdapter: YouTubeVideoAdapter
    private var video: YouTubeVideo? = null

    override fun getTheme(): Int = R.style.PlayerBottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_VIDEO, YouTubeVideo::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_VIDEO)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadsViewModel.initialize(requireContext())
        setupUI()
        setupWebView()
        setupRelatedVideos()
        observeViewModel()
        observeDownloadState()
        video?.let { loadVideo(it) }
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { dialog ->
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    private fun setupUI() {
        binding.dragHandle.setOnClickListener { dismiss() }
        
        binding.ivPlayButton.setOnClickListener {
            video?.let { v ->
                openVideoInYouTube(v.id)
            }
        }
        
        binding.tvChannelInfo.setOnClickListener {
            video?.let { v ->
                navigateToChannel(v.channelId)
            }
        }
        
        binding.btnDownload.setOnClickListener {
            video?.let { v ->
                showDownloadMenu(v, it)
            }
        }
    }

    private fun setupWebView() {
        // WebView is no longer used - videos open in YouTube app/browser
    }

    private fun setupRelatedVideos() {
        relatedAdapter = YouTubeVideoAdapter(
            onVideoClick = { relatedVideo ->
                loadVideo(relatedVideo)
                viewModel.playVideo(relatedVideo)
            },
            onChannelClick = { video ->
                // Navigate to channel
                navigateToChannel(video.channelId)
            },
            onDownloadClick = { video, anchor ->
                showDownloadMenu(video, anchor)
            }
        )
        binding.rvRelatedVideos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = relatedAdapter
            isNestedScrollingEnabled = false
        }
    }
    
    private fun showDownloadMenu(video: YouTubeVideo, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add("Video + Audio")
        popup.menu.add("Audio only")
        
        popup.setOnMenuItemClickListener { menuItem ->
            val formatId = when (menuItem.title) {
                "Video + Audio" -> "best"
                "Audio only" -> "bestaudio"
                else -> "best"
            }
            
            // Set extracting state
            DownloadStateManager.setState(video.id, DownloadStateManager.DownloadState.EXTRACTING, formatId = formatId)
            
            val videoUrl = "https://www.youtube.com/watch?v=${video.id}"
            
            Toast.makeText(
                requireContext(),
                "Preparing download...",
                Toast.LENGTH_SHORT
            ).show()
            
            // Extract content and download
            downloadsViewModel.extractAndDownload(videoUrl, formatId, video.id)
            
            true
        }
        
        popup.show()
    }

    private fun loadVideo(video: YouTubeVideo) {
        this.video = video
        binding.apply {
            tvTitle.text = video.title
            tvChannelInfo.text = video.channelName

            Glide.with(this@VideoPlayerFragment)
                .load(video.thumbnailUrl)
                .placeholder(R.drawable.ic_music_note)
                .centerCrop()
                .into(ivVideoThumbnail)
        }
        
        showThumbnail()
        viewModel.loadRelatedVideos(video.id)
    }

    private fun openVideoInYouTube(videoId: String) {
        try {
            // Try to open in YouTube app first
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
            startActivity(intent)
        } catch (_: Exception) {
            // Fallback to browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
            startActivity(intent)
        }
    }

    private fun showThumbnail() {
        binding.apply {
            youtubePlayerView.visibility = View.GONE
            thumbnailContainer.visibility = View.VISIBLE
        }
    }

    private fun navigateToChannel(channelId: String) {
        // Dismiss the bottom sheet first
        dismiss()
        
        // Get the BrowseFragment from the activity's fragment manager
        val activity = requireActivity()
        val browseFragment = activity.supportFragmentManager.findFragmentById(R.id.browseContainer) 
            as? BrowseFragment
        
        browseFragment?.openChannelById(channelId)
    }

    private fun observeViewModel() {
        viewModel.relatedVideos.observe(viewLifecycleOwner) { videos ->
            relatedAdapter.submitList(videos)
        }
    }
    
    private fun observeDownloadState() {
        lifecycleScope.launch {
            DownloadStateManager.downloadStates.collectLatest { states ->
                video?.let { v ->
                    val info = states[v.id] ?: DownloadStateManager.DownloadInfo()
                    updateDownloadUI(info)
                }
            }
        }
    }
    
    private fun updateDownloadUI(info: DownloadStateManager.DownloadInfo) {
        binding.apply {
            when (info.state) {
                DownloadStateManager.DownloadState.EXTRACTING -> {
                    btnDownload.visibility = View.INVISIBLE
                    progressExtracting.visibility = View.VISIBLE
                    progressDownloading.visibility = View.GONE
                }
                DownloadStateManager.DownloadState.DOWNLOADING -> {
                    btnDownload.visibility = View.INVISIBLE
                    progressExtracting.visibility = View.GONE
                    progressDownloading.visibility = View.VISIBLE
                    progressDownloading.progress = info.progress
                }
                DownloadStateManager.DownloadState.COMPLETED -> {
                    btnDownload.visibility = View.VISIBLE
                    progressExtracting.visibility = View.GONE
                    progressDownloading.visibility = View.GONE
                }
                else -> {
                    btnDownload.visibility = View.VISIBLE
                    progressExtracting.visibility = View.GONE
                    progressDownloading.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "VideoPlayerFragment"
        private const val ARG_VIDEO = "arg_video"

        fun newInstance(video: YouTubeVideo): VideoPlayerFragment {
            return VideoPlayerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_VIDEO, video)
                }
            }
        }
    }
}
