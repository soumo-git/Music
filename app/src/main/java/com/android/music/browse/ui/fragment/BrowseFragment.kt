package com.android.music.browse.ui.fragment

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.android.music.R
import com.android.music.browse.auth.YouTubeAuthManager
import com.android.music.browse.data.model.StreamingPlatform
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.browse.ui.adapter.BrowseTabAdapter
import com.android.music.browse.ui.viewmodel.BrowseViewModel
import com.android.music.browse.ui.viewmodel.SpotifyViewModel
import com.android.music.databinding.FragmentBrowseBinding
import com.google.android.material.tabs.TabLayoutMediator

class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowseViewModel by activityViewModels()
    private val spotifyViewModel: SpotifyViewModel by activityViewModels()
    private lateinit var tabAdapter: BrowseTabAdapter
    private lateinit var authManager: YouTubeAuthManager

    // Activity result launcher for Google Sign-In
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val signInResult = authManager.handleSignInResult(result.data)
            signInResult.onSuccess { account ->
                Toast.makeText(
                    requireContext(),
                    "Signed in as ${account.displayName}",
                    Toast.LENGTH_SHORT
                ).show()
                updateAuthUI()
                // Refresh current tab data
                viewModel.currentTab.value?.let { viewModel.setCurrentTab(it) }
            }.onFailure { e ->
                Toast.makeText(
                    requireContext(),
                    "Sign-in failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = viewModel.getAuthManager()
        setupPlatformSelector()
        setupSearch()
        setupLinkInput()
        setupSignInButton()
        setupTabs()
        setupBackStackListener()
        observeViewModel()
        
        // Default to YouTube platform
        val initialPlatform = viewModel.currentPlatform.value ?: StreamingPlatform.YOUTUBE
        selectPlatform(initialPlatform)

        // Then update auth-dependent UI while respecting the currently selected platform
        updateAuthUI()
    }

    private fun setupBackStackListener() {
        childFragmentManager.addOnBackStackChangedListener {
            val hasBackStack = childFragmentManager.backStackEntryCount > 0
            binding.browseFragmentContainer.visibility = if (hasBackStack) View.VISIBLE else View.GONE
            binding.mainContent.visibility = if (hasBackStack) View.GONE else View.VISIBLE
        }
    }

    private fun setupPlatformSelector() {
        binding.platformSelector.setOnClickListener { view ->
            showPlatformMenu(view)
        }
    }

    private fun showPlatformMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)

        // Add platforms only (no YouTube sign-in/sign-out entries)
        StreamingPlatform.entries.forEach { platform ->
            popup.menu.add(platform.displayName).setOnMenuItemClickListener {
                selectPlatform(platform)
                true
            }
        }
        popup.show()
    }

    private fun setupSignInButton() {
        binding.btnSignIn.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        val signInIntent = authManager.getSignInIntent()
        signInLauncher.launch(signInIntent)
    }

    private fun selectPlatform(platform: StreamingPlatform) {
        viewModel.setPlatform(platform)
        binding.tvPlatformName.text = platform.displayName
        binding.ivPlatformIcon.setImageResource(platform.iconRes)
        
        // Show icon for platforms
        binding.ivPlatformIcon.visibility = View.VISIBLE
        // Show the search bar
        binding.searchBarContainer.visibility = View.VISIBLE
        
        // Handle Spotify platform
        if (platform == StreamingPlatform.SPOTIFY) {
            binding.tabLayout.visibility = View.GONE
            binding.viewPager.visibility = View.GONE
            binding.spotifyContainer.visibility = View.VISIBLE
            showSpotify()
            updateAuthUI()
            return
        }

        // Handle YouTube platform (default)
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        binding.spotifyContainer.visibility = View.GONE

        // Apply auth-dependent UI for streaming platforms
        updateAuthUI()
    }

    private fun showSpotify() {
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.spotifyContainer.visibility = View.VISIBLE
        
        // Add SpotifyFragment if not already added
        if (childFragmentManager.findFragmentById(R.id.spotifyContainer) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.spotifyContainer, SpotifyFragment.newInstance())
                .commit()
        }
    }

    private fun setupSearch() {
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString()
                if (query.isNotBlank()) {
                    val currentPlatform = viewModel.currentPlatform.value ?: StreamingPlatform.YOUTUBE
                    
                    when (currentPlatform) {
                        StreamingPlatform.YOUTUBE -> viewModel.search(query)
                        StreamingPlatform.SPOTIFY -> spotifyViewModel.search(query)
                    }
                    
                    // Hide keyboard
                    binding.etSearch.clearFocus()
                }
                true
            } else {
                false
            }
        }
        
        // Clear search when focus lost and empty
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.etSearch.text.isNullOrBlank()) {
                val currentPlatform = viewModel.currentPlatform.value ?: StreamingPlatform.YOUTUBE
                when (currentPlatform) {
                    StreamingPlatform.YOUTUBE -> viewModel.clearSearch()
                    StreamingPlatform.SPOTIFY -> spotifyViewModel.clearSearch()
                }
            }
        }
    }

    private fun setupLinkInput() {
        // Link input removed - now in Downloads tab
    }

    private fun setupTabs() {
        tabAdapter = BrowseTabAdapter(this)
        binding.viewPager.adapter = tabAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabAdapter.getTabTitle(position)
        }.attach()
    }

    private fun updateAuthUI() {
        val currentPlatform = viewModel.currentPlatform.value ?: StreamingPlatform.YOUTUBE

        // Handle Spotify platform
        if (currentPlatform == StreamingPlatform.SPOTIFY) {
            val isAuthenticated = spotifyViewModel.isAuthenticated.value ?: false
            if (isAuthenticated) {
                binding.tabLayout.visibility = View.GONE
                binding.viewPager.visibility = View.GONE
                binding.signInContainer.visibility = View.GONE
                binding.spotifyContainer.visibility = View.VISIBLE
                
                // Load content if not cached
                if (spotifyViewModel.homeContent.value == null) {
                    spotifyViewModel.loadHomeContent()
                }
            } else {
                binding.tabLayout.visibility = View.GONE
                binding.viewPager.visibility = View.GONE
                binding.signInContainer.visibility = View.VISIBLE
                binding.spotifyContainer.visibility = View.GONE
            }
            return
        }

        // Handle YouTube platform
        val isAuthenticated = authManager.isAuthenticated()
        if (isAuthenticated) {
            // Show tabs and content for streaming platforms
            binding.tabLayout.visibility = View.VISIBLE
            binding.viewPager.visibility = View.VISIBLE
            binding.signInContainer.visibility = View.GONE
            binding.spotifyContainer.visibility = View.GONE

            // Only load content if not already cached
            if (viewModel.homeContent.value == null) {
                viewModel.loadHomeContent()
            }
        } else {
            // Show sign-in prompt for streaming platforms
            binding.tabLayout.visibility = View.GONE
            binding.viewPager.visibility = View.GONE
            binding.signInContainer.visibility = View.VISIBLE
            binding.spotifyContainer.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.currentPlatform.observe(viewLifecycleOwner) { platform ->
            binding.tvPlatformName.text = platform.displayName
            binding.ivPlatformIcon.setImageResource(platform.iconRes)
        }
        
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
        
        viewModel.isAuthenticated.observe(viewLifecycleOwner) { _ ->
            updateAuthUI()
        }
        
        spotifyViewModel.isAuthenticated.observe(viewLifecycleOwner) { _ ->
            updateAuthUI()
        }
    }

    fun openVideoPlayer(video: YouTubeVideo) {
        val playerFragment = VideoPlayerFragment.newInstance(video)
        playerFragment.show(childFragmentManager, VideoPlayerFragment.TAG)
    }

    fun openChannel(channel: com.android.music.browse.data.model.YouTubeChannel) {
        binding.browseFragmentContainer.visibility = View.VISIBLE
        binding.mainContent.visibility = View.GONE
        val channelFragment = ChannelFragment.newInstance(channel)
        childFragmentManager.beginTransaction()
            .replace(R.id.browseFragmentContainer, channelFragment)
            .addToBackStack(ChannelFragment.TAG)
            .commit()
    }

    fun openChannelById(channelId: String) {
        binding.browseFragmentContainer.visibility = View.VISIBLE
        binding.mainContent.visibility = View.GONE
        val channelFragment = ChannelFragment.newInstance(channelId)
        childFragmentManager.beginTransaction()
            .replace(R.id.browseFragmentContainer, channelFragment)
            .addToBackStack(ChannelFragment.TAG)
            .commit()
    }

    fun openPlaylist(playlist: com.android.music.browse.data.model.YouTubePlaylist) {
        binding.browseFragmentContainer.visibility = View.VISIBLE
        binding.mainContent.visibility = View.GONE
        val playlistFragment = PlaylistVideosFragment.newInstance(playlist)
        childFragmentManager.beginTransaction()
            .replace(R.id.browseFragmentContainer, playlistFragment)
            .addToBackStack(PlaylistVideosFragment.TAG)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = BrowseFragment()
    }
}
