package com.android.music.duo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.R
import com.android.music.databinding.FragmentDuoSongsBinding
import com.android.music.duo.chat.ui.DuoChatBottomSheet
import com.android.music.duo.data.model.DuoSortOption
import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.android.music.ui.adapter.SongAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DuoSongsFragment : Fragment() {

    private var _binding: FragmentDuoSongsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DuoViewModel by activityViewModels()
    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDuoSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song ->
                // Both devices can control playback
                viewModel.playSong(song)
            },
            onSongOptionClick = { _, _ -> }
        )

        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        binding.btnShuffle.setOnClickListener {
            // Both devices can control playback
            viewModel.shufflePlay()
        }

        binding.btnSort.setOnClickListener { view ->
            showSortMenu(view)
        }
        
        binding.btnResync.setOnClickListener {
            viewModel.resyncLibrary()
        }
        
        binding.btnChat.setOnClickListener {
            openChatSheet()
        }
    }
    
    private fun openChatSheet() {
        DuoChatBottomSheet.newInstance()
            .show(childFragmentManager, DuoChatBottomSheet.TAG)
    }

    private fun showSortMenu(view: View) {
        PopupMenu(requireContext(), view).apply {
            menuInflater.inflate(R.menu.menu_duo_sort, menu)

            setOnMenuItemClickListener { menuItem ->
                val sortOption = when (menuItem.itemId) {
                    R.id.sort_name -> DuoSortOption.NAME
                    R.id.sort_time -> DuoSortOption.TIME
                    R.id.sort_duration -> DuoSortOption.DURATION
                    else -> return@setOnMenuItemClickListener false
                }
                viewModel.setSortOption(sortOption)
                true
            }

            show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredSongs.collectLatest { songs ->
                android.util.Log.d("DuoSongsFragment", "Received ${songs.size} filtered songs")
                songAdapter.submitList(songs)
                binding.emptyState.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
                binding.rvSongs.visibility = if (songs.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentSong.collectLatest { song ->
                songAdapter.setCurrentPlayingSong(song?.id)
            }
        }
        
        // Observe unread messages for badge
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hasUnreadMessages.collectLatest { hasUnread ->
                binding.chatBadge.visibility = if (hasUnread) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = DuoSongsFragment()
    }
}
