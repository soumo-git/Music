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
import com.android.music.data.model.Artist
import com.android.music.databinding.FragmentArtistsBinding
import com.android.music.ui.adapter.ArtistAdapter
import com.android.music.ui.viewmodel.MusicViewModel

class ArtistsFragment : Fragment() {

    private var _binding: FragmentArtistsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var artistAdapter: ArtistAdapter
    
    private var selectionBar: View? = null
    
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (artistAdapter.isInSelectionMode()) {
                artistAdapter.clearSelection()
                hideSelectionBar()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun setupRecyclerView() {
        artistAdapter = ArtistAdapter(
            onArtistClick = { artist ->
                viewModel.selectArtist(artist)
            },
            onArtistOptionClick = { artist, option ->
                when (option) {
                    ArtistAdapter.ArtistOption.SHARE -> {
                        val songs = viewModel.getSongsForArtist(artist)
                        viewModel.shareSongs(requireContext(), songs)
                    }
                }
            },
            onSelectionChanged = { selectedArtists ->
                if (selectedArtists.isNotEmpty()) {
                    showSelectionBar(selectedArtists.size)
                    backPressedCallback.isEnabled = true
                } else {
                    hideSelectionBar()
                    backPressedCallback.isEnabled = false
                }
            }
        )

        binding.rvArtists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = artistAdapter
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
                val selectedArtists = artistAdapter.getSelectedArtists()
                val allSongs = selectedArtists.flatMap { viewModel.getSongsForArtist(it) }
                viewModel.shareSongs(requireContext(), allSongs)
                artistAdapter.clearSelection()
                hideSelectionBar()
            }
            bar.findViewById<android.widget.ImageButton>(R.id.btnClose)?.setOnClickListener {
                artistAdapter.clearSelection()
                hideSelectionBar()
            }
        }
    }
    
    private fun hideSelectionBar() {
        selectionBar?.visibility = View.GONE
    }

    private fun observeViewModel() {
        viewModel.artists.observe(viewLifecycleOwner) { artists ->
            artistAdapter.submitList(artists)
            binding.emptyState.visibility = if (artists.isEmpty()) View.VISIBLE else View.GONE
            binding.rvArtists.visibility = if (artists.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        selectionBar = null
    }

    companion object {
        fun newInstance() = ArtistsFragment()
    }
}
