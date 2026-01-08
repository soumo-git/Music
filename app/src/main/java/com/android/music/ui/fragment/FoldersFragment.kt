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
import com.android.music.data.model.Folder
import com.android.music.databinding.FragmentFoldersBinding
import com.android.music.ui.adapter.FolderAdapter
import com.android.music.ui.viewmodel.MusicViewModel

class FoldersFragment : Fragment() {

    private var _binding: FragmentFoldersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var folderAdapter: FolderAdapter
    
    private var selectionBar: View? = null
    
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (folderAdapter.isInSelectionMode()) {
                folderAdapter.clearSelection()
                hideSelectionBar()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onFolderClick = { folder ->
                // Navigate to FolderContentsActivity with both songs and videos
                val songs = viewModel.getSongsForFolder(folder)
                val videos = viewModel.getVideosForFolder(folder)
                com.android.music.ui.activity.FolderContentsActivity.start(
                    requireContext(),
                    folder.name,
                    songs,
                    videos
                )
            },
            onFolderOptionClick = { folder, option ->
                when (option) {
                    FolderAdapter.FolderOption.SHARE -> {
                        val songs = viewModel.getSongsForFolder(folder)
                        viewModel.shareSongs(requireContext(), songs)
                    }
                }
            },
            onSelectionChanged = { selectedFolders ->
                if (selectedFolders.isNotEmpty()) {
                    showSelectionBar(selectedFolders.size)
                    backPressedCallback.isEnabled = true
                } else {
                    hideSelectionBar()
                    backPressedCallback.isEnabled = false
                }
            }
        )

        binding.rvFolders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = folderAdapter
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
                val selectedFolders = folderAdapter.getSelectedFolders()
                val allSongs = selectedFolders.flatMap { viewModel.getSongsForFolder(it) }
                viewModel.shareSongs(requireContext(), allSongs)
                folderAdapter.clearSelection()
                hideSelectionBar()
            }
            bar.findViewById<android.widget.ImageButton>(R.id.btnClose)?.setOnClickListener {
                folderAdapter.clearSelection()
                hideSelectionBar()
            }
        }
    }
    
    private fun hideSelectionBar() {
        selectionBar?.visibility = View.GONE
    }

    private fun observeViewModel() {
        viewModel.folders.observe(viewLifecycleOwner) { folders ->
            folderAdapter.submitList(folders)
            binding.emptyState.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
            binding.rvFolders.visibility = if (folders.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        selectionBar = null
    }

    companion object {
        fun newInstance() = FoldersFragment()
    }
}
