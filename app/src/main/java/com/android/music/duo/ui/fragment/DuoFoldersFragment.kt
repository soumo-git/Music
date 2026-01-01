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
import com.android.music.databinding.FragmentDuoFoldersBinding
import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.android.music.ui.adapter.FolderAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for Duo Folders tab
 * Shows folders that contain common songs between connected devices
 * Note: Folder names may differ between devices, but we show folders containing common songs
 */
class DuoFoldersFragment : Fragment() {

    private var _binding: FragmentDuoFoldersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DuoViewModel by activityViewModels()
    private lateinit var folderAdapter: FolderAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDuoFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter { folder ->
            // Get songs from this folder from common songs
            val folderSongs = viewModel.getSongsForFolder(folder.path)
            if (folderSongs.isNotEmpty()) {
                Toast.makeText(requireContext(), "${folder.name}: ${folderSongs.size} songs", Toast.LENGTH_SHORT).show()
                // Play first song from this folder
                viewModel.playSong(folderSongs.first())
            }
        }

        binding.rvFolders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = folderAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredFolders.collectLatest { folders ->
                android.util.Log.d("DuoFoldersFragment", "Received ${folders.size} folders: ${folders.map { it.name }}")
                folderAdapter.submitList(folders)
                binding.emptyState.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
                binding.rvFolders.visibility = if (folders.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = DuoFoldersFragment()
    }
}
