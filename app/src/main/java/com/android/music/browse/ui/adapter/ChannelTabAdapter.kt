package com.android.music.browse.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.music.browse.ui.fragment.ChannelVideosFragment
import com.android.music.browse.ui.fragment.ChannelShortsFragment
import com.android.music.browse.ui.fragment.ChannelPlaylistsFragment

class ChannelTabAdapter(
    fragment: Fragment,
    private val channelId: String
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ChannelVideosFragment.newInstance(channelId)
            1 -> ChannelShortsFragment.newInstance(channelId)
            2 -> ChannelPlaylistsFragment.newInstance(channelId)
            else -> ChannelVideosFragment.newInstance(channelId)
        }
    }
}
