package com.android.music.ui.activity

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.data.model.Song
import com.android.music.databinding.FragmentFolderSongsBinding
import com.android.music.ui.adapter.SongAdapter

class FolderSongsFragment : Fragment() {

    private var _binding: FragmentFolderSongsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var songAdapter: SongAdapter
    private var songs: List<Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        songs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelableArrayList(ARG_SONGS, Song::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList(ARG_SONGS) ?: emptyList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupShuffleButton()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song ->
                (activity as? FolderContentsActivity)?.playSong(song)
            },
            onSongOptionClick = { song, option ->
                Toast.makeText(requireContext(), "${option.name}: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )
        songAdapter.submitList(songs)

        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
            setHasFixedSize(true)
        }
        
        binding.emptyState.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        binding.rvSongs.visibility = if (songs.isEmpty()) View.GONE else View.VISIBLE
        binding.btnShuffle.visibility = if (songs.isEmpty()) View.GONE else View.VISIBLE
    }
    
    private fun setupShuffleButton() {
        binding.btnShuffle.setOnClickListener {
            if (songs.isNotEmpty()) {
                (activity as? FolderContentsActivity)?.shufflePlaySongs(songs)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SONGS = "arg_songs"
        
        fun newInstance(songs: List<Song>): FolderSongsFragment {
            return FolderSongsFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_SONGS, ArrayList(songs))
                }
            }
        }
    }
}
