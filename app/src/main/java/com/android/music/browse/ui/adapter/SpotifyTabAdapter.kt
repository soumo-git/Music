package com.android.music.browse.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.music.browse.ui.fragment.SpotifyHomeFragment
import com.android.music.browse.ui.fragment.SpotifyLibraryFragment
import com.android.music.browse.ui.fragment.SpotifyProfileFragment

class SpotifyTabAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val tabs = listOf(
        TabInfo("Home", SpotifyHomeFragment::class.java),
        TabInfo("Your Library", SpotifyLibraryFragment::class.java),
        TabInfo("Profile", SpotifyProfileFragment::class.java)
    )

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return tabs[position].fragmentClass.getDeclaredConstructor().newInstance()
    }

    fun getTabTitle(position: Int): String = tabs[position].title

    data class TabInfo(
        val title: String,
        val fragmentClass: Class<out Fragment>
    )
}
