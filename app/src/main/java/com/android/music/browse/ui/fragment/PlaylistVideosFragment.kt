package com.android.music.browse.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.R
import com.android.music.browse.data.model.YouTubePlaylist
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.browse.ui.adapter.YouTubeVideoAdapter
import com.android.music.browse.ui.viewmodel.BrowseViewModel
import com.android.music.databinding.FragmentPlaylistVideosBinding
import com.android.music.download.manager.DownloadStateManager
import com.android.music.download.ui.viewmodel.DownloadsViewModel
import com.bumptech.glide.Glide
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistVideosFragment : Fragment() {

    private var _binding: FragmentPlaylistVideosBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowseViewModel by activityViewModels()
    private val downloadsViewModel: DownloadsViewModel by activityViewModels()
    private lateinit var videoAdapter: YouTubeVideoAdapter
    
    private var playlist: YouTubePlaylist? = null
    private var playlistId: String? = null
    private var channelId: String? = null
    
    // Use playlist ID as the download tracking key
    private val playlistDownloadKey: String
        get() = "playlist_${playlistId ?: playlist?.id ?: ""}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlist = arguments?.getParcelable(ARG_PLAYLIST)
        playlistId = arguments?.getString(ARG_PLAYLIST_ID) ?: playlist?.id
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadsViewModel.initialize(requireContext())
        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        observePlaylistDownloadState()
        
        playlist?.let { displayPlaylistInfo(it) }
        playlistId?.let { viewModel.loadPlaylistVideos(it) }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            // Pop from BrowseFragment's child fragment manager
            val browseFragment = parentFragment as? BrowseFragment
            browseFragment?.childFragmentManager?.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        videoAdapter = YouTubeVideoAdapter(
            onVideoClick = { video ->
                viewModel.playVideo(video)
                openVideoPlayer(video)
            },
            onChannelClick = { video ->
                // Navigate to channel
                val browseFragment = parentFragment as? BrowseFragment
                browseFragment?.openChannelById(video.channelId)
            },
            onDownloadClick = { video, anchor ->
                showDownloadMenu(video, anchor)
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = videoAdapter
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
            
            // Extract content and download
            downloadsViewModel.extractAndDownload(videoUrl, formatId, video.id)
            
            true
        }
        
        popup.show()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            playlistId?.let { viewModel.loadPlaylistVideos(it) }
        }
    }

    private fun displayPlaylistInfo(playlist: YouTubePlaylist) {
        channelId = playlist.channelId
        
        binding.apply {
            tvPlaylistTitle.text = playlist.title
            tvPlaylistInfo.text = "${playlist.videoCount} videos · ${playlist.channelName}"

            playlist.thumbnailUrl?.let { url ->
                Glide.with(this@PlaylistVideosFragment)
                    .load(url)
                    .placeholder(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(ivPlaylistThumbnail)
            }
            
            // Make channel name clickable
            tvPlaylistInfo.setOnClickListener {
                channelId?.let { id ->
                    val browseFragment = parentFragment as? BrowseFragment
                    browseFragment?.openChannelById(id)
                }
            }
            
            // Download button - download entire playlist
            btnDownload.setOnClickListener {
                showPlaylistDownloadMenu(it)
            }
        }
    }
    
    private fun showPlaylistDownloadMenu(anchor: View) {
        val id = playlistId ?: playlist?.id
        if (id == null) {
            Toast.makeText(requireContext(), "Playlist not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add("Video + Audio")
        popup.menu.add("Audio only")
        
        popup.setOnMenuItemClickListener { menuItem ->
            val formatId = when (menuItem.title) {
                "Video + Audio" -> "best"
                "Audio only" -> "bestaudio"
                else -> "best"
            }
            
            // Set extracting state for playlist
            DownloadStateManager.setState(playlistDownloadKey, DownloadStateManager.DownloadState.EXTRACTING, formatId = formatId)
            
            // Use playlist URL format
            val playlistUrl = "https://www.youtube.com/playlist?list=$id"
            
            Toast.makeText(
                requireContext(),
                "Preparing playlist download...",
                Toast.LENGTH_SHORT
            ).show()
            
            // Extract content and download entire playlist
            downloadsViewModel.extractAndDownload(playlistUrl, formatId, playlistDownloadKey)
            
            true
        }
        
        popup.show()
    }
    
    private fun observePlaylistDownloadState() {
        lifecycleScope.launch {
            DownloadStateManager.downloadStates.collectLatest { states ->
                val info = states[playlistDownloadKey] ?: DownloadStateManager.DownloadInfo()
                updatePlaylistDownloadUI(info)
            }
        }
    }
    
    private fun updatePlaylistDownloadUI(info: DownloadStateManager.DownloadInfo) {
        binding.apply {
            when (info.state) {
                DownloadStateManager.DownloadState.EXTRACTING -> {
                    btnDownload.visibility = View.INVISIBLE
                    progressExtracting.visibility = View.VISIBLE
                    progressDownloading.visibility = View.GONE
                    tvPlaylistProgress?.visibility = View.GONE
                }
                DownloadStateManager.DownloadState.DOWNLOADING -> {
                    btnDownload.visibility = View.INVISIBLE
                    progressExtracting.visibility = View.GONE
                    progressDownloading.visibility = View.VISIBLE
                    progressDownloading.progress = info.progress
                    
                    // Show playlist progress if available
                    if (info.isPlaylist && info.totalItems > 0) {
                        tvPlaylistProgress?.visibility = View.VISIBLE
                        tvPlaylistProgress?.text = "${info.completedItems}/${info.totalItems} songs"
                    } else {
                        tvPlaylistProgress?.visibility = View.GONE
                    }
                }
                DownloadStateManager.DownloadState.COMPLETED -> {
                    btnDownload.visibility = View.VISIBLE
                    progressExtracting.visibility = View.GONE
                    progressDownloading.visibility = View.GONE
                    tvPlaylistProgress?.visibility = View.GONE
                }
                else -> {
                    btnDownload.visibility = View.VISIBLE
                    progressExtracting.visibility = View.GONE
                    progressDownloading.visibility = View.GONE
                    tvPlaylistProgress?.visibility = View.GONE
                }
            }
        }
    }

    private fun openVideoPlayer(video: YouTubeVideo) {
        val playerFragment = VideoPlayerFragment.newInstance(video)
        playerFragment.show(childFragmentManager, VideoPlayerFragment.TAG)
    }

    private fun observeViewModel() {
        viewModel.playlistVideos.observe(viewLifecycleOwner) { videos ->
            videoAdapter.submitList(videos)
            binding.emptyState.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
            binding.progressBar.visibility = View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isLoading) {
                binding.swipeRefresh.isRefreshing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PlaylistVideosFragment"
        private const val ARG_PLAYLIST = "arg_playlist"
        private const val ARG_PLAYLIST_ID = "arg_playlist_id"

        fun newInstance(playlist: YouTubePlaylist): PlaylistVideosFragment {
            return PlaylistVideosFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PLAYLIST, playlist)
                }
            }
        }

        fun newInstance(playlistId: String): PlaylistVideosFragment {
            return PlaylistVideosFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLAYLIST_ID, playlistId)
                }
            }
        }
    }
}
