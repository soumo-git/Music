package com.android.music.duo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.databinding.FragmentDuoVideosBinding
import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.android.music.ui.adapter.VideoAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for Duo Videos tab
 * Shows common videos between connected devices
 */
class DuoVideosFragment : Fragment() {

    private var _binding: FragmentDuoVideosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DuoViewModel by activityViewModels()
    private lateinit var videoAdapter: VideoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDuoVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter(
            onVideoClick = { video ->
                // Play video using LocalVideoPlayerActivity
                com.android.music.videoplayer.ui.LocalVideoPlayerActivity.start(
                    requireContext(), 
                    video, 
                    videoAdapter.currentList
                )
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
    
    private fun handleVideoOption(video: com.android.music.data.model.Video, option: VideoAdapter.VideoOption) {
        when (option) {
            VideoAdapter.VideoOption.SHARE -> shareVideo(video)
            VideoAdapter.VideoOption.DELETE -> {
                android.widget.Toast.makeText(requireContext(), "Cannot delete in Duo mode", android.widget.Toast.LENGTH_SHORT).show()
            }
            VideoAdapter.VideoOption.INFO -> showVideoInfo(video)
        }
    }
    
    private fun shareVideo(video: com.android.music.data.model.Video) {
        try {
            val file = java.io.File(video.path)
            if (!file.exists()) {
                android.widget.Toast.makeText(requireContext(), "File not found", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share video"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "Failed to share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showVideoInfo(video: com.android.music.data.model.Video) {
        val file = java.io.File(video.path)
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
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), com.android.music.R.style.DarkAlertDialog)
            .setTitle("Video Info")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredVideos.collectLatest { videos ->
                android.util.Log.d("DuoVideosFragment", "Received ${videos.size} videos")
                videoAdapter.submitList(videos)
                binding.emptyState.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
                binding.rvVideos.visibility = if (videos.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = DuoVideosFragment()
    }
}
