package com.android.music.ui.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.R
import com.android.music.data.model.Video
import com.android.music.databinding.FragmentVideosBinding
import com.android.music.databinding.LayoutVideoPlayerBarBinding
import com.android.music.ui.adapter.VideoAdapter
import com.android.music.ui.viewmodel.MusicViewModel
import com.android.music.videoplayer.ui.LocalVideoPlayerActivity
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class VideosFragment : Fragment() {

    private var _binding: FragmentVideosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var videoAdapter: VideoAdapter
    
    private var videoPlayerBarBinding: LayoutVideoPlayerBarBinding? = null
    private var allVideos: List<Video> = emptyList()
    
    private val videoStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LocalVideoPlayerActivity.BROADCAST_VIDEO_STATE -> {
                    val isPlaying = intent.getBooleanExtra(LocalVideoPlayerActivity.EXTRA_IS_PLAYING, false)
                    val title = intent.getStringExtra(LocalVideoPlayerActivity.EXTRA_VIDEO_TITLE)
                    val position = intent.getLongExtra(LocalVideoPlayerActivity.EXTRA_POSITION, 0L)
                    val duration = intent.getLongExtra(LocalVideoPlayerActivity.EXTRA_DURATION, 0L)
                    updateVideoPlayerBar(isPlaying, title, position, duration)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupVideoPlayerBar()
        observeViewModel()
        registerReceivers()
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter(
            onVideoClick = { video ->
                playVideo(video)
            },
            onVideoOptionClick = { video, option ->
                handleVideoOption(video, option)
            }
        )

        binding.rvVideos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = videoAdapter
            setHasFixedSize(true)
        }
    }
    
    private fun setupVideoPlayerBar() {
        // Inflate video player bar into the container
        videoPlayerBarBinding = LayoutVideoPlayerBarBinding.inflate(
            layoutInflater, 
            binding.videoPlayerBarContainer, 
            true
        )
        
        videoPlayerBarBinding?.apply {
            root.setOnClickListener {
                // Reopen video player
                LocalVideoPlayerActivity.getCurrentVideo()?.let { video ->
                    LocalVideoPlayerActivity.start(requireContext(), video, allVideos)
                }
            }
            
            btnPlayPause.setOnClickListener {
                LocalVideoPlayerActivity.togglePlayPause()
                updatePlayPauseButton()
            }
            
            btnClose.setOnClickListener {
                LocalVideoPlayerActivity.stopPlayback()
                binding.videoPlayerBarContainer.visibility = View.GONE
            }
        }
        
        // Initially hidden
        binding.videoPlayerBarContainer.visibility = View.GONE
    }
    
    private fun playVideo(video: Video) {
        LocalVideoPlayerActivity.start(requireContext(), video, allVideos)
        videoAdapter.setCurrentPlayingVideo(video.id)
        
        // Show player bar
        binding.videoPlayerBarContainer.visibility = View.VISIBLE
        updateVideoPlayerBarUI(video)
    }
    
    private fun updateVideoPlayerBarUI(video: Video) {
        videoPlayerBarBinding?.apply {
            tvVideoTitle.text = video.title
            tvVideoDuration.text = video.formattedDuration
            
            Glide.with(this@VideosFragment)
                .load(video.thumbnailUri)
                .placeholder(R.drawable.ic_video)
                .centerCrop()
                .into(ivVideoThumbnail)
        }
    }
    
    private fun updateVideoPlayerBar(isPlaying: Boolean, title: String?, position: Long, duration: Long) {
        if (title == null) {
            binding.videoPlayerBarContainer.visibility = View.GONE
            return
        }
        
        binding.videoPlayerBarContainer.visibility = View.VISIBLE
        
        videoPlayerBarBinding?.apply {
            tvVideoTitle.text = title
            
            if (duration > 0) {
                val positionStr = formatDuration(position)
                val durationStr = formatDuration(duration)
                tvVideoDuration.text = "$positionStr / $durationStr"
                progressBar.max = duration.toInt()
                progressBar.progress = position.toInt()
            }
            
            btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
    }
    
    private fun updatePlayPauseButton() {
        val isPlaying = LocalVideoPlayerActivity.isPlaying()
        videoPlayerBarBinding?.btnPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
    
    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000 / 60) % 60
        val hours = ms / 1000 / 3600
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    private fun handleVideoOption(video: Video, option: VideoAdapter.VideoOption) {
        when (option) {
            VideoAdapter.VideoOption.SHARE -> shareVideo(video)
            VideoAdapter.VideoOption.DELETE -> confirmDeleteVideo(video)
            VideoAdapter.VideoOption.INFO -> showVideoInfo(video)
        }
    }
    
    private fun shareVideo(video: Video) {
        try {
            val file = File(video.path)
            if (!file.exists()) {
                Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share video"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun confirmDeleteVideo(video: Video) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("Delete Video")
            .setMessage("Are you sure you want to delete \"${video.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteVideo(video)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteVideo(video: Video) {
        try {
            val file = File(video.path)
            if (file.exists() && file.delete()) {
                Toast.makeText(requireContext(), "Video deleted", Toast.LENGTH_SHORT).show()
                viewModel.loadAllMedia()
            } else {
                Toast.makeText(requireContext(), "Failed to delete video", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showVideoInfo(video: Video) {
        val file = File(video.path)
        val sizeInMB = if (file.exists()) {
            String.format("%.2f MB", file.length() / (1024.0 * 1024.0))
        } else {
            "Unknown"
        }
        
        val info = """
            Title: ${video.title}
            Artist: ${video.artist}
            Duration: ${video.formattedDuration}
            Size: $sizeInMB
            Path: ${video.path}
        """.trimIndent()
        
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("Video Info")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(LocalVideoPlayerActivity.BROADCAST_VIDEO_STATE)
        }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(videoStateReceiver, filter)
    }

    private fun observeViewModel() {
        viewModel.videos.observe(viewLifecycleOwner) { videos ->
            allVideos = videos
            videoAdapter.submitList(videos)
            binding.emptyState.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
            binding.rvVideos.visibility = if (videos.isEmpty()) View.GONE else View.VISIBLE
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check if video is still playing and update bar
        LocalVideoPlayerActivity.getCurrentVideo()?.let { video ->
            binding.videoPlayerBarContainer.visibility = View.VISIBLE
            updateVideoPlayerBarUI(video)
            updatePlayPauseButton()
            videoAdapter.setCurrentPlayingVideo(video.id)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(videoStateReceiver)
        videoPlayerBarBinding = null
        _binding = null
    }

    companion object {
        fun newInstance() = VideosFragment()
    }
}
