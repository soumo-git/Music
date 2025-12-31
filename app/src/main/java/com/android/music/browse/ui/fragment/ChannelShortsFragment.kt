package com.android.music.browse.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.android.music.browse.ui.adapter.ShortsAdapter
import com.android.music.browse.ui.viewmodel.BrowseViewModel
import com.android.music.databinding.FragmentChannelVideosBinding

class ChannelShortsFragment : Fragment() {

    private var _binding: FragmentChannelVideosBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowseViewModel by activityViewModels()
    private lateinit var shortsAdapter: ShortsAdapter
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
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        
        channelId?.let { viewModel.loadChannelShorts(it) }
    }

    private fun setupRecyclerView() {
        shortsAdapter = ShortsAdapter { video ->
            viewModel.playVideo(video)
            // Open video player through ChannelFragment
            (parentFragment as? ChannelFragment)?.openVideoPlayer(video)
        }
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = shortsAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            channelId?.let { viewModel.loadChannelShorts(it) }
        }
    }

    private fun observeViewModel() {
        viewModel.channelShorts.observe(viewLifecycleOwner) { shorts ->
            shortsAdapter.submitList(shorts)
            binding.emptyState.visibility = if (shorts.isEmpty()) View.VISIBLE else View.GONE
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

        fun newInstance(channelId: String): ChannelShortsFragment {
            return ChannelShortsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHANNEL_ID, channelId)
                }
            }
        }
    }
}
