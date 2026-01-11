package com.android.music.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.android.music.databinding.ActivitySettingsBinding
import com.android.music.download.engine.ui.EngineSettingsFragment
import com.android.music.videoplayer.engine.ui.VideoEngineSettingsFragment
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Settings Activity for app-wide settings
 */
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupTabs()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupTabs() {
        val fragments = listOf(
            Pair("Download Engine", EngineSettingsFragment.newInstance()),
            Pair("Video Engine", VideoEngineSettingsFragment.newInstance())
        )
        
        val adapter = SettingsPagerAdapter(this, fragments)
        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = fragments[position].first
        }.attach()
    }
    
    private class SettingsPagerAdapter(
        activity: AppCompatActivity,
        private val fragments: List<Pair<String, Fragment>>
    ) : androidx.viewpager2.adapter.FragmentStateAdapter(activity) {
        
        override fun getItemCount(): Int = fragments.size
        
        override fun createFragment(position: Int): Fragment = fragments[position].second
    }
}
