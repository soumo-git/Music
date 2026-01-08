package com.android.music.ui.activity

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.R
import com.android.music.data.model.Video
import com.android.music.databinding.FragmentFolderVideosBinding
import com.android.music.ui.adapter.VideoAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class FolderVideosFragment : Fragment() {

    private var _binding: FragmentFolderVideosBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var videoAdapter: VideoAdapter
    private var videos: List<Video> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelableArrayList(ARG_VIDEOS, Video::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList(ARG_VIDEOS) ?: emptyList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter(
            onVideoClick = { video ->
                (activity as? FolderContentsActivity)?.playVideo(video)
            },
            onVideoOptionClick = { video, option ->
                handleVideoOption(video, option)
            }
        )
        videoAdapter.submitList(videos)

        binding.rvVideos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = videoAdapter
            setHasFixedSize(true)
        }
        
        binding.emptyState.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
        binding.rvVideos.visibility = if (videos.isEmpty()) View.GONE else View.VISIBLE
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
            
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share video"))
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
                // Remove from list and refresh
                val updatedList = videos.filter { it.id != video.id }
                videos = updatedList
                videoAdapter.submitList(updatedList)
                binding.emptyState.visibility = if (updatedList.isEmpty()) View.VISIBLE else View.GONE
                binding.rvVideos.visibility = if (updatedList.isEmpty()) View.GONE else View.VISIBLE
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_VIDEOS = "arg_videos"
        
        fun newInstance(videos: List<Video>): FolderVideosFragment {
            return FolderVideosFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_VIDEOS, ArrayList(videos))
                }
            }
        }
    }
}
