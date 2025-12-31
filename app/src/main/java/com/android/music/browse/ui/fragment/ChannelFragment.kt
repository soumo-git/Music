package com.android.music.browse.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.music.R
import com.android.music.browse.data.model.YouTubeChannel
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.browse.ui.adapter.ChannelTabAdapter
import com.android.music.browse.ui.viewmodel.BrowseViewModel
import com.android.music.databinding.FragmentChannelBinding
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class ChannelFragment : Fragment() {

    private var _binding: FragmentChannelBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowseViewModel by activityViewModels()
    
    private var channel: YouTubeChannel? = null
    private var channelId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channel = arguments?.getParcelable(ARG_CHANNEL)
        channelId = arguments?.getString(ARG_CHANNEL_ID) ?: channel?.id
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChannelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupTabs()
        
        channel?.let { 
            displayChannelInfo(it)
            loadChannelVideos(it.id)
        } ?: channelId?.let {
            loadChannelDetails(it)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            // Pop from BrowseFragment's child fragment manager
            val browseFragment = parentFragment as? BrowseFragment
            browseFragment?.childFragmentManager?.popBackStack()
        }
    }

    private fun setupTabs() {
        val tabAdapter = ChannelTabAdapter(this, channelId ?: "")
        binding.viewPager.adapter = tabAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Videos"
                1 -> "Shorts"
                2 -> "Playlists"
                else -> ""
            }
        }.attach()
    }

    private fun displayChannelInfo(channel: YouTubeChannel) {
        binding.apply {
            tvChannelName.text = channel.name
            tvSubscriberCount.text = channel.subscriberCount?.let { "$it subscribers" } ?: ""
            tvVideoCount.text = channel.videoCount?.let { "$it videos" } ?: ""

            channel.thumbnailUrl?.let { url ->
                Glide.with(this@ChannelFragment)
                    .load(url)
                    .placeholder(R.drawable.ic_music_note)
                    .circleCrop()
                    .into(ivChannelAvatar)
            }
        }
    }

    private fun loadChannelDetails(channelId: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            viewModel.loadChannelDetails(channelId).collect { result ->
                binding.progressBar.visibility = View.GONE
                result.onSuccess { channelDetails ->
                    channel = channelDetails
                    displayChannelInfo(channelDetails)
                    loadChannelVideos(channelId)
                }.onFailure { e ->
                    Toast.makeText(requireContext(), "Failed to load channel: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadChannelVideos(channelId: String) {
        viewModel.loadChannelVideos(channelId)
    }

    fun openVideoPlayer(video: YouTubeVideo) {
        val playerFragment = VideoPlayerFragment.newInstance(video)
        playerFragment.show(childFragmentManager, VideoPlayerFragment.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ChannelFragment"
        private const val ARG_CHANNEL = "arg_channel"
        private const val ARG_CHANNEL_ID = "arg_channel_id"

        fun newInstance(channel: YouTubeChannel): ChannelFragment {
            return ChannelFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_CHANNEL, channel)
                }
            }
        }

        fun newInstance(channelId: String): ChannelFragment {
            return ChannelFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHANNEL_ID, channelId)
                }
            }
        }
    }
}
