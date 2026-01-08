package com.android.music.duo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.databinding.FragmentDuoArtistsBinding
import com.android.music.duo.ui.activity.DuoSongsListActivity
import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.android.music.ui.adapter.ArtistAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for Duo Artists tab
 * Shows common artists between connected devices (derived from common songs)
 */
class DuoArtistsFragment : Fragment() {

    private var _binding: FragmentDuoArtistsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DuoViewModel by activityViewModels()
    private lateinit var artistAdapter: ArtistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDuoArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        artistAdapter = ArtistAdapter(
            onArtistClick = { artist ->
                // Get songs by this artist from common songs and open songs list
                val artistSongs = viewModel.getSongsForArtist(artist.name)
                if (artistSongs.isNotEmpty()) {
                    DuoSongsListActivity.start(requireContext(), artist.name, artistSongs)
                }
            }
        )

        binding.rvArtists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = artistAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredArtists.collectLatest { artists ->
                android.util.Log.d("DuoArtistsFragment", "Received ${artists.size} artists")
                artistAdapter.submitList(artists)
                binding.emptyState.visibility = if (artists.isEmpty()) View.VISIBLE else View.GONE
                binding.rvArtists.visibility = if (artists.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = DuoArtistsFragment()
    }
}
