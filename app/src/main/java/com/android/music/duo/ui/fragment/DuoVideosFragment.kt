package com.android.music.duo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
        videoAdapter = VideoAdapter { video ->
            // TODO: Play video in Duo mode (video sync not yet implemented)
            Toast.makeText(requireContext(), "Video playback sync coming soon: ${video.title}", Toast.LENGTH_SHORT).show()
        }

        binding.rvVideos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = videoAdapter
            setHasFixedSize(true)
        }
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
