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
import com.android.music.databinding.FragmentBrowseHomeBinding
import com.android.music.download.manager.DownloadStateManager
import com.android.music.download.ui.viewmodel.DownloadsViewModel

class BrowseHomeFragment : Fragment() {

    private var _binding: FragmentBrowseHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowseViewModel by activityViewModels()
    private val downloadsViewModel: DownloadsViewModel by activityViewModels()
    private lateinit var videoAdapter: YouTubeVideoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadsViewModel.initialize(requireContext())
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        
        // Only load if we don't have cached content
        if (viewModel.homeContent.value == null) {
            viewModel.loadHomeContent()
        }
    }

    private fun setupRecyclerView() {
        videoAdapter = YouTubeVideoAdapter(
            onVideoClick = { video ->
                viewModel.playVideo(video)
                // Navigate to video player
                (parentFragment as? BrowseFragment)?.openVideoPlayer(video)
            },
            onChannelClick = { video ->
                // Navigate to channel
                (parentFragment as? BrowseFragment)?.openChannelById(video.channelId)
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
            
            // Extract content first
            downloadsViewModel.extractAndDownload(videoUrl, formatId, video.id)
            
            true
        }
        
        popup.show()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            if (viewModel.isSearchMode.value == true) {
                viewModel.searchQuery.value?.let { viewModel.search(it) }
            } else {
                viewModel.loadHomeContent()
            }
        }
    }

    private fun observeViewModel() {
        // Observe home content
        viewModel.homeContent.observe(viewLifecycleOwner) { content ->
            if (viewModel.isSearchMode.value != true) {
                val allVideos = content.trendingVideos + content.recommendedVideos
                videoAdapter.submitList(allVideos.distinctBy { it.id })
            }
            binding.swipeRefresh.isRefreshing = false
        }

        // Observe search results
        viewModel.searchResults.observe(viewLifecycleOwner) { searchResult ->
            if (viewModel.isSearchMode.value == true) {
                videoAdapter.submitList(searchResult.videos)
            }
            binding.swipeRefresh.isRefreshing = false
        }

        // Observe search mode changes
        viewModel.isSearchMode.observe(viewLifecycleOwner) { isSearchMode ->
            if (!isSearchMode) {
                // Restore home content when search is cleared
                viewModel.homeContent.value?.let { content ->
                    val allVideos = content.trendingVideos + content.recommendedVideos
                    videoAdapter.submitList(allVideos.distinctBy { it.id })
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isLoading) {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
