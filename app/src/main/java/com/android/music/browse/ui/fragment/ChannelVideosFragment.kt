package com.android.music.browse.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.browse.ui.adapter.YouTubeVideoAdapter
import com.android.music.browse.ui.viewmodel.BrowseViewModel
import com.android.music.databinding.FragmentChannelVideosBinding
import com.android.music.download.manager.DownloadStateManager
import com.android.music.download.ui.viewmodel.DownloadsViewModel

class ChannelVideosFragment : Fragment() {

    private var _binding: FragmentChannelVideosBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowseViewModel by activityViewModels()
    private val downloadsViewModel: DownloadsViewModel by activityViewModels()
    private lateinit var videoAdapter: YouTubeVideoAdapter
    private var channelId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelId = arguments?.getString(ARG_CHANNEL_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChannelVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadsViewModel.initialize(requireContext())
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        
        channelId?.let { viewModel.loadChannelVideos(it) }
    }

    private fun setupRecyclerView() {
        videoAdapter = YouTubeVideoAdapter(
            onVideoClick = { video ->
                viewModel.playVideo(video)
                // Open video player through ChannelFragment
                (parentFragment as? ChannelFragment)?.openVideoPlayer(video)
            },
            onChannelClick = { video ->
                // Navigate to channel (in case video is from different channel)
                val channelFragment = parentFragment as? ChannelFragment
                val browseFragment = channelFragment?.parentFragment as? BrowseFragment
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
            channelId?.let { viewModel.loadChannelVideos(it) }
        }
    }

    private fun observeViewModel() {
        viewModel.channelVideos.observe(viewLifecycleOwner) { videos ->
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
        private const val ARG_CHANNEL_ID = "arg_channel_id"

        fun newInstance(channelId: String): ChannelVideosFragment {
            return ChannelVideosFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHANNEL_ID, channelId)
                }
            }
        }
    }
}
