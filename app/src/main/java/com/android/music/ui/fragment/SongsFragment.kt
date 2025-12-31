package com.android.music.ui.fragment

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.R
import com.android.music.data.model.Song
import com.android.music.data.model.SortOption
import com.android.music.databinding.FragmentSongsBinding
import com.android.music.service.MusicService
import com.android.music.ui.adapter.SongAdapter
import com.android.music.ui.viewmodel.MusicViewModel

class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var songAdapter: SongAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.loadAllMedia()
            Toast.makeText(requireContext(), "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Some permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicService.BROADCAST_PLAYBACK_STATE -> {
                    val song = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(MusicService.EXTRA_SONG, Song::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(MusicService.EXTRA_SONG)
                    }
                    song?.let { songAdapter.setCurrentPlayingSong(it.id) }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        registerReceivers()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song ->
                viewModel.setPlaylist(viewModel.songs.value ?: emptyList())
                viewModel.playSong(song)
            },
            onSongOptionClick = { song, option -> handleSongOption(song, option) }
        )

        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
            setHasFixedSize(true)
            // Enable smooth scrolling for high refresh rate displays
            itemAnimator?.changeDuration = 150
            itemAnimator?.addDuration = 150
            itemAnimator?.removeDuration = 150
            itemAnimator?.moveDuration = 150
        }
    }

    private fun setupClickListeners() {
        binding.btnShuffle.setOnClickListener {
            viewModel.shufflePlay()
        }

        binding.btnSort.setOnClickListener { view ->
            showSortMenu(view)
        }

        binding.btnAskPermissions.setOnClickListener {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val permissions = getRequiredPermissions()
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            Toast.makeText(requireContext(), "All permissions already granted", Toast.LENGTH_SHORT).show()
            viewModel.loadAllMedia()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showSortMenu(view: View) {
        PopupMenu(requireContext(), view).apply {
            menuInflater.inflate(R.menu.menu_sort, menu)
            
            setOnMenuItemClickListener { menuItem ->
                val sortOption = when (menuItem.itemId) {
                    R.id.sort_adding_time -> SortOption.ADDING_TIME
                    R.id.sort_name -> SortOption.NAME
                    R.id.sort_play_count -> SortOption.PLAY_COUNT
                    else -> return@setOnMenuItemClickListener false
                }
                viewModel.setSortOption(sortOption)
                true
            }
            
            show()
        }
    }

    private fun handleSongOption(song: Song, option: SongAdapter.SongOption) {
        when (option) {
            SongAdapter.SongOption.PLAY_LATER -> {
                Toast.makeText(requireContext(), "Added to play later", Toast.LENGTH_SHORT).show()
            }
            SongAdapter.SongOption.ADD_TO_QUEUE -> {
                Toast.makeText(requireContext(), "Added to queue", Toast.LENGTH_SHORT).show()
            }
            SongAdapter.SongOption.DELETE -> {
                Toast.makeText(requireContext(), "Delete: ${song.title}", Toast.LENGTH_SHORT).show()
            }
            SongAdapter.SongOption.SHARE -> {
                viewModel.shareSong(requireContext(), song)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            songAdapter.submitList(songs)
            binding.emptyState.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            binding.rvSongs.visibility = if (songs.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.currentSong.observe(viewLifecycleOwner) { song ->
            songAdapter.setCurrentPlayingSong(song?.id)
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(MusicService.BROADCAST_PLAYBACK_STATE)
        }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(playbackReceiver, filter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(playbackReceiver)
        _binding = null
    }

    companion object {
        fun newInstance() = SongsFragment()
    }
}
