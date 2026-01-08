package com.android.music.duo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.android.music.databinding.FragmentDuoAlbumsBinding
import com.android.music.duo.ui.activity.DuoSongsListActivity
import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.android.music.ui.adapter.AlbumAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for Duo Albums tab
 * Shows common albums between connected devices (derived from common songs)
 */
class DuoAlbumsFragment : Fragment() {

    private var _binding: FragmentDuoAlbumsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DuoViewModel by activityViewModels()
    private lateinit var albumAdapter: AlbumAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDuoAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        albumAdapter = AlbumAdapter(
            onAlbumClick = { album ->
                // Get songs from this album from common songs and open songs list
                val albumSongs = viewModel.getSongsForAlbum(album.title, album.artist)
                if (albumSongs.isNotEmpty()) {
                    DuoSongsListActivity.start(requireContext(), album.title, albumSongs)
                }
            }
        )

        binding.rvAlbums.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = albumAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredAlbums.collectLatest { albums ->
                android.util.Log.d("DuoAlbumsFragment", "Received ${albums.size} albums")
                albumAdapter.submitList(albums)
                binding.emptyState.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
                binding.rvAlbums.visibility = if (albums.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = DuoAlbumsFragment()
    }
}
