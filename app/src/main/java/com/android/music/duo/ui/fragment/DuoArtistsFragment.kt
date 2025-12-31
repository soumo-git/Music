package com.android.music.duo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.android.music.databinding.FragmentDuoSongsBinding

/**
 * Placeholder fragment for Duo Artists tab
 * Will show common artists between connected devices
 */
class DuoArtistsFragment : Fragment() {

    private var _binding: FragmentDuoSongsBinding? = null
    private val binding get() = _binding!!

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
        // Show empty state for now
        binding.emptyState.visibility = View.VISIBLE
        binding.rvSongs.visibility = View.GONE
        binding.shuffleSortRow.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = DuoArtistsFragment()
    }
}
