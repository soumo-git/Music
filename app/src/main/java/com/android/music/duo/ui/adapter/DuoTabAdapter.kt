package com.android.music.duo.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.music.duo.ui.fragment.DuoSongsFragment
import com.android.music.duo.ui.fragment.DuoVideosFragment
import com.android.music.duo.ui.fragment.DuoArtistsFragment
import com.android.music.duo.ui.fragment.DuoAlbumsFragment
import com.android.music.duo.ui.fragment.DuoFoldersFragment

class DuoTabAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val tabTitles = listOf("Songs", "Videos", "Artists", "Albums", "Folders")

    override fun getItemCount(): Int = tabTitles.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DuoSongsFragment.newInstance()
            1 -> DuoVideosFragment.newInstance()
            2 -> DuoArtistsFragment.newInstance()
            3 -> DuoAlbumsFragment.newInstance()
            4 -> DuoFoldersFragment.newInstance()
            else -> DuoSongsFragment.newInstance()
        }
    }

    fun getTabTitle(position: Int): String = tabTitles[position]
}
