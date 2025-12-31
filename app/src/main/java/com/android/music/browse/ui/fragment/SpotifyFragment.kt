package com.android.music.browse.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.android.music.browse.ui.adapter.SpotifyTabAdapter
import com.android.music.browse.ui.viewmodel.SpotifyViewModel
import com.android.music.databinding.FragmentSpotifyBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Main Spotify fragment that manages tabs (Home, Library, Profile)
 */
class SpotifyFragment : Fragment() {

    private var _binding: FragmentSpotifyBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SpotifyViewModel by activityViewModels()
    private lateinit var tabAdapter: SpotifyTabAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpotifyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        observeViewModel()
    }

    private fun setupTabs() {
        tabAdapter = SpotifyTabAdapter(this)
        binding.viewPager.adapter = tabAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabAdapter.getTabTitle(position)
        }.attach()
    }

    private fun observeViewModel() {
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = SpotifyFragment()
    }
}
