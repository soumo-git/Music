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

class SpotifyLibraryFragment : Fragment() {

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
        
        // Load library content
        if (viewModel.savedTracks.value == null) {
            viewModel.loadSavedTracks()
        }
    }

    private fun setupRecyclerView() {
        trackAdapter = SpotifyTrackAdapter(
            onTrackClick = { track ->
                viewModel.playTrack(track)
                Toast.makeText(requireContext(), "Playing: ${track.name}", Toast.LENGTH_SHORT).show()
            },
            onArtistClick = { track ->
                Toast.makeText(requireContext(), "Artist: ${track.artistName}", Toast.LENGTH_SHORT).show()
            },
            onMoreClick = { track, anchor ->
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
            viewModel.loadSavedTracks()
        }
    }

    private fun observeViewModel() {
        viewModel.savedTracks.observe(viewLifecycleOwner) { tracks ->
            trackAdapter.submitList(tracks)
            binding.swipeRefresh.isRefreshing = false
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
