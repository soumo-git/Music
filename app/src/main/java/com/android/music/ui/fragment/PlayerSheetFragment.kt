package com.android.music.ui.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.music.R
import com.android.music.data.model.Song
import com.android.music.databinding.LayoutPlayerSheetBinding
import com.android.music.duo.data.model.DuoConnectionState
import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.android.music.equalizer.ui.EqualizerActivity
import com.android.music.service.MusicService
import com.android.music.ui.viewmodel.MusicViewModel
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerSheetFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutPlayerSheetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MusicViewModel by activityViewModels()
    private val duoViewModel: DuoViewModel by activityViewModels()
    
    private var isPlaying = false
    private var currentSong: Song? = null
    private var isDuoConnected = false

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicService.BROADCAST_PROGRESS -> {
                    val position = intent.getIntExtra(MusicService.EXTRA_POSITION, 0)
                    val duration = intent.getIntExtra(MusicService.EXTRA_DURATION, 0)
                    updateProgress(position, duration)
                }
                MusicService.BROADCAST_PLAYBACK_STATE -> {
                    val playing = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    val song = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(MusicService.EXTRA_SONG, Song::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(MusicService.EXTRA_SONG)
                    }
                    updatePlaybackState(playing, song)
                }
            }
        }
    }

    override fun getTheme(): Int = R.style.PlayerBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutPlayerSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupControls()
        observeViewModel()
        registerReceivers()
    }

    override fun onStart() {
        super.onStart()
        // Set the bottom sheet to expand properly
        dialog?.let { dialog ->
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
                
                // Make background transparent to show content behind
                it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    private fun setupUI() {
        binding.btnCollapse.setOnClickListener {
            dismiss()
        }
        
        binding.dragHandle.setOnClickListener {
            dismiss()
        }
        
        // Equalizer button click
        binding.btnEqualizer.setOnClickListener {
            val intent = Intent(requireContext(), EqualizerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            if (isDuoConnected) {
                // Send through DuoViewModel to sync with partner
                if (isPlaying) {
                    duoViewModel.pause()
                } else {
                    duoViewModel.resume()
                }
            }
            // Always send to local service
            val action = if (isPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_RESUME
            requireContext().startService(Intent(requireContext(), MusicService::class.java).apply {
                this.action = action
            })
        }

        binding.btnNext.setOnClickListener {
            if (isDuoConnected) {
                // Send through DuoViewModel which will sync to partner
                duoViewModel.playNext()
                return@setOnClickListener
            }
            requireContext().startService(Intent(requireContext(), MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            })
        }

        binding.btnPrevious.setOnClickListener {
            if (isDuoConnected) {
                // Send through DuoViewModel which will sync to partner
                duoViewModel.playPrevious()
                return@setOnClickListener
            }
            requireContext().startService(Intent(requireContext(), MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            })
        }

        binding.btnRepeat.setOnClickListener {
            if (isDuoConnected) {
                duoViewModel.toggleRepeat()
            }
            requireContext().startService(Intent(requireContext(), MusicService::class.java).apply {
                action = MusicService.ACTION_TOGGLE_REPEAT
            })
        }

        binding.seekBarPlayer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (isDuoConnected) {
                        // Send seek through DuoViewModel to sync with partner
                        duoViewModel.seekTo(progress.toLong())
                    }
                    requireContext().startService(Intent(requireContext(), MusicService::class.java).apply {
                        action = MusicService.ACTION_SEEK
                        putExtra(MusicService.EXTRA_POSITION, progress)
                    })
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun observeViewModel() {
        viewModel.currentSong.observe(viewLifecycleOwner) { song ->
            song?.let { updateSongUI(it) }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            isPlaying = playing
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
        
        // Observe Duo connection state
        viewLifecycleOwner.lifecycleScope.launch {
            duoViewModel.connectionState.collectLatest { state ->
                isDuoConnected = state is DuoConnectionState.Connected
            }
        }
    }

    private fun updateSongUI(song: Song) {
        currentSong = song
        binding.tvSongTitle.text = song.title
        binding.tvArtistName.text = "@${song.artist}"
        
        Glide.with(this)
            .load(song.albumArtUri)
            .signature(com.bumptech.glide.signature.ObjectKey(song.id.toString()))
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .centerCrop()
            .into(binding.ivAlbumArt)
    }

    private fun updateProgress(position: Int, duration: Int) {
        binding.seekBarPlayer.max = duration
        binding.seekBarPlayer.progress = position
        binding.tvCurrentTime.text = formatTime(position)
        binding.tvTotalTime.text = formatTime(duration)
    }

    private fun updatePlaybackState(playing: Boolean, song: Song?) {
        isPlaying = playing
        binding.btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play
        )
        song?.let { updateSongUI(it) }
    }

    private fun formatTime(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(MusicService.BROADCAST_PROGRESS)
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
        const val TAG = "PlayerSheetFragment"
        
        fun newInstance(): PlayerSheetFragment {
            return PlayerSheetFragment()
        }
    }
}
