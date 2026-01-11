package com.android.music.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.R
import com.android.music.databinding.FragmentAlbumsBinding
import com.android.music.ui.adapter.AlbumAdapter
import com.android.music.ui.viewmodel.MusicViewModel

class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var albumAdapter: AlbumAdapter
    
    private var selectionBar: View? = null
    
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (albumAdapter.isInSelectionMode()) {
                albumAdapter.clearSelection()
                hideSelectionBar()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun setupRecyclerView() {
        albumAdapter = AlbumAdapter(
            onAlbumClick = { album ->
                viewModel.selectAlbum(album)
            },
            onAlbumOptionClick = { album, option ->
                when (option) {
                    AlbumAdapter.AlbumOption.SHARE -> {
                        val songs = viewModel.getSongsForAlbum(album)
                        viewModel.shareSongs(requireContext(), songs)
                    }
                }
            },
            onSelectionChanged = { selectedAlbums ->
                if (selectedAlbums.isNotEmpty()) {
                    showSelectionBar(selectedAlbums.size)
                    backPressedCallback.isEnabled = true
                } else {
                    hideSelectionBar()
                    backPressedCallback.isEnabled = false
                }
            }
        )

        binding.rvAlbums.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = albumAdapter
            setHasFixedSize(true)
        }
    }
    
    private fun showSelectionBar(count: Int) {
        if (selectionBar == null) {
            selectionBar = layoutInflater.inflate(R.layout.selection_bar, binding.root as ViewGroup, false)
            (binding.root as ViewGroup).addView(selectionBar)
        }
        
        selectionBar?.let { bar ->
            bar.visibility = View.VISIBLE
            bar.findViewById<android.widget.TextView>(R.id.tvSelectionCount)?.text = "$count selected"
            bar.findViewById<android.widget.ImageButton>(R.id.btnShare)?.setOnClickListener {
                val selectedAlbums = albumAdapter.getSelectedAlbums()
                val allSongs = selectedAlbums.flatMap { viewModel.getSongsForAlbum(it) }
                viewModel.shareSongs(requireContext(), allSongs)
                albumAdapter.clearSelection()
                hideSelectionBar()
            }
            bar.findViewById<android.widget.ImageButton>(R.id.btnClose)?.setOnClickListener {
                albumAdapter.clearSelection()
                hideSelectionBar()
            }
        }
    }
    
    private fun hideSelectionBar() {
        selectionBar?.visibility = View.GONE
    }

    private fun observeViewModel() {
        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            albumAdapter.submitList(albums)
            binding.emptyState.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
            binding.rvAlbums.visibility = if (albums.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        selectionBar = null
    }

    companion object {
        fun newInstance() = AlbumsFragment()
    }
}
