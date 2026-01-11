package com.android.music.browse.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.browse.ui.adapter.SpotifyTrackAdapter
import com.android.music.browse.ui.viewmodel.SpotifyViewModel
import com.android.music.databinding.FragmentSpotifyHomeBinding

class SpotifyHomeFragment : Fragment() {

    private var _binding: FragmentSpotifyHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SpotifyViewModel by activityViewModels()
    private lateinit var trackAdapter: SpotifyTrackAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpotifyHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        
        // Load content if not cached
        if (viewModel.homeContent.value == null) {
            viewModel.loadHomeContent()
        }
    }

    private fun setupRecyclerView() {
        trackAdapter = SpotifyTrackAdapter(
            onTrackClick = { track ->
                viewModel.playTrack(track)
                // TODO: Open Spotify player
                Toast.makeText(requireContext(), "Playing: ${track.name}", Toast.LENGTH_SHORT).show()
            },
            onArtistClick = { track ->
                // TODO: Navigate to artist page
                Toast.makeText(requireContext(), "Artist: ${track.artistName}", Toast.LENGTH_SHORT).show()
            },
            onMoreClick = { _, _ ->
                // TODO: Show options menu
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = trackAdapter
        }
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
                // Show recommendations from home content
                trackAdapter.submitList(content.recommendations)
            }
            binding.swipeRefresh.isRefreshing = false
        }

        // Observe search results
        viewModel.searchResults.observe(viewLifecycleOwner) { searchResult ->
            if (viewModel.isSearchMode.value == true) {
                trackAdapter.submitList(searchResult.tracks)
            }
            binding.swipeRefresh.isRefreshing = false
        }

        // Observe search mode changes
        viewModel.isSearchMode.observe(viewLifecycleOwner) { isSearchMode ->
            if (!isSearchMode) {
                // Restore home content when search is cleared
                viewModel.homeContent.value?.let { content ->
                    trackAdapter.submitList(content.recommendations)
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isLoading) {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
