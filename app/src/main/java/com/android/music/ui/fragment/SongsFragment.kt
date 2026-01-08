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
import androidx.activity.OnBackPressedCallback
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var songAdapter: SongAdapter
    
    private var selectionBar: View? = null

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
                MusicService.BROADCAST_QUEUE_UPDATE -> {
                    val queueSize = intent.getIntExtra(MusicService.EXTRA_QUEUE_SIZE, 0)
                    if (queueSize > 0) {
                        Toast.makeText(requireContext(), "Added to queue ($queueSize in queue)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (songAdapter.isInSelectionMode()) {
                songAdapter.clearSelection()
                hideSelectionBar()
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
        viewModel.initializePlayCountManager(requireContext())
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        registerReceivers()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song ->
                viewModel.setPlaylist(viewModel.songs.value ?: emptyList())
                viewModel.playSong(song)
            },
            onSongOptionClick = { song, option -> handleSongOption(song, option) },
            onSelectionChanged = { selectedSongs ->
                if (selectedSongs.isNotEmpty()) {
                    showSelectionBar(selectedSongs.size)
                    backPressedCallback.isEnabled = true
                } else {
                    hideSelectionBar()
                    backPressedCallback.isEnabled = false
                }
            }
        )

        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
            setHasFixedSize(true)
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
    
    private fun showSelectionBar(count: Int) {
        if (selectionBar == null) {
            selectionBar = layoutInflater.inflate(R.layout.selection_bar, binding.root as ViewGroup, false)
            (binding.root as ViewGroup).addView(selectionBar)
        }
        
        selectionBar?.let { bar ->
            bar.visibility = View.VISIBLE
            bar.findViewById<android.widget.TextView>(R.id.tvSelectionCount)?.text = "$count selected"
            bar.findViewById<android.widget.ImageButton>(R.id.btnShare)?.setOnClickListener {
                val selectedSongs = songAdapter.getSelectedSongs()
                viewModel.shareSongs(requireContext(), selectedSongs)
                songAdapter.clearSelection()
                hideSelectionBar()
            }
            bar.findViewById<android.widget.ImageButton>(R.id.btnClose)?.setOnClickListener {
                songAdapter.clearSelection()
                hideSelectionBar()
            }
        }
    }
    
    private fun hideSelectionBar() {
        selectionBar?.visibility = View.GONE
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
            SongAdapter.SongOption.ADD_TO_QUEUE -> {
                viewModel.addToQueue(requireContext(), song)
            }
            SongAdapter.SongOption.DELETE -> {
                showDeleteConfirmation(song)
            }
            SongAdapter.SongOption.SHARE -> {
                viewModel.shareSong(requireContext(), song)
            }
        }
    }
    
    private fun showDeleteConfirmation(song: Song) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.delete_song_confirm, song.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteSong(requireContext(), song)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        
        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                if (it.success) {
                    Toast.makeText(requireContext(), getString(R.string.song_deleted, it.songTitle), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                }
                viewModel.clearDeleteResult()
            }
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(MusicService.BROADCAST_PLAYBACK_STATE)
            addAction(MusicService.BROADCAST_QUEUE_UPDATE)
        }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(playbackReceiver, filter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(playbackReceiver)
        _binding = null
        selectionBar = null
    }

    companion object {
        fun newInstance() = SongsFragment()
    }
}
