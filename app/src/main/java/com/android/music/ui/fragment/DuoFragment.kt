package com.android.music.ui.fragment

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.music.R
import com.android.music.databinding.FragmentDuoBinding
import com.android.music.duo.data.model.DuoConnectionState
import com.android.music.duo.data.model.SignalStrength
import com.android.music.duo.ui.adapter.DuoTabAdapter
import com.android.music.duo.ui.fragment.DuoConnectionBottomSheet
import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.android.music.duo.webrtc.SignalingManager
import com.android.music.duo.webrtc.model.WebRTCConnectionState
import com.android.music.ui.viewmodel.MusicViewModel
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Duo feature fragment - Sync music playback between two devices over WiFi Direct
 */
class DuoFragment : Fragment() {

    private var _binding: FragmentDuoBinding? = null
    private val binding get() = _binding!!

    private val duoViewModel: DuoViewModel by activityViewModels()
    private val musicViewModel: MusicViewModel by activityViewModels()
    
    private var tabAdapter: DuoTabAdapter? = null
    private var welcomeAnimator: ObjectAnimator? = null
    
    // Track current connection type for UI
    private var isWebRTCConnected = false
    private var isWifiDirectConnected = false
    
    // Track if incoming request dialog is showing
    private var incomingRequestDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDuoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWelcomeUI()
        setupConnectedUI()
        setupClickListeners()
        observeViewModel()
        syncLocalSongs()
    }

    private fun setupWelcomeUI() {
        // Load Duo icon with animation
        Glide.with(this)
            .load("file:///android_asset/Duo.png")
            .into(binding.ivWelcomeDuoIcon)

        // Start pulse animation - but don't let it consume clicks
        startWelcomeAnimation()
    }

    private fun startWelcomeAnimation() {
        welcomeAnimator?.cancel()
        
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f, 1f)
        
        welcomeAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.ivWelcomeDuoIcon, scaleX, scaleY).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun setupConnectedUI() {
        // Load Duo icon for connected state
        Glide.with(this)
            .load("file:///android_asset/Duo.png")
            .into(binding.ivDuoIcon)

        // Setup tabs
        tabAdapter = DuoTabAdapter(this)
        binding.viewPager.adapter = tabAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabAdapter?.getTabTitle(position)
        }.attach()
    }

    private fun setupClickListeners() {
        // Connect button (welcome state)
        binding.btnConnectDuo.setOnClickListener {
            showConnectionSheet()
        }
        
        // Welcome Duo icon container click (before connection) - opens connection sheet
        binding.welcomeDuoIconContainer.setOnClickListener {
            android.util.Log.d("DuoFragment", "Welcome Duo icon clicked!")
            showConnectionSheet()
        }

        // Duo header click (connected state) - the entire header container
        binding.duoHeaderClickable.setOnClickListener {
            android.util.Log.d("DuoFragment", "Duo header (connected state) clicked!")
            showConnectionSheet()
        }

        // Search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                duoViewModel.setSearchQuery(s?.toString() ?: "")
            }
        })
    }

    private fun showConnectionSheet() {
        DuoConnectionBottomSheet.newInstance()
            .show(childFragmentManager, DuoConnectionBottomSheet.TAG)
    }

    private fun observeViewModel() {
        // Observe WiFi Direct connection state
        viewLifecycleOwner.lifecycleScope.launch {
            duoViewModel.connectionState.collectLatest { state ->
                android.util.Log.d("DuoFragment", "WiFi Direct connection state changed: $state")
                updateUIForConnectionState(state)
            }
        }
        
        // Observe WebRTC connection state
        viewLifecycleOwner.lifecycleScope.launch {
            duoViewModel.webRTCConnectionState.collectLatest { state ->
                android.util.Log.d("DuoFragment", "WebRTC connection state changed: $state")
                updateUIForWebRTCState(state)
            }
        }
        
        // Observe incoming WebRTC requests and show popup dialog
        viewLifecycleOwner.lifecycleScope.launch {
            duoViewModel.incomingWebRTCOffer.collectLatest { offer ->
                if (offer != null) {
                    showIncomingRequestDialog(offer)
                } else {
                    dismissIncomingRequestDialog()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            duoViewModel.signalStrength.collectLatest { strength ->
                updateSignalStrengthIcon(strength)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            duoViewModel.toastMessage.collectLatest { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
        
        // Debug: observe common songs
        viewLifecycleOwner.lifecycleScope.launch {
            duoViewModel.commonSongs.collectLatest { songs ->
                android.util.Log.d("DuoFragment", "Common songs updated: ${songs.size}")
            }
        }
        
        // Debug: observe filtered songs
        viewLifecycleOwner.lifecycleScope.launch {
            duoViewModel.filteredSongs.collectLatest { songs ->
                android.util.Log.d("DuoFragment", "Filtered songs updated: ${songs.size}")
            }
        }
    }
    
    /**
     * Show incoming WebRTC connection request as a popup dialog
     */
    private fun showIncomingRequestDialog(offer: SignalingManager.IncomingOffer) {
        // Dismiss any existing dialog
        dismissIncomingRequestDialog()
        
        incomingRequestDialog = MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(R.string.duo_incoming_request)
            .setMessage(getString(R.string.duo_request_from, offer.fromDeviceName))
            .setIcon(R.drawable.ic_smartphone)
            .setPositiveButton(R.string.duo_accept) { _, _ ->
                duoViewModel.acceptIncomingOffer(offer)
            }
            .setNegativeButton(R.string.duo_decline) { _, _ ->
                duoViewModel.rejectIncomingOffer(offer)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Dismiss incoming request dialog if showing
     */
    private fun dismissIncomingRequestDialog() {
        incomingRequestDialog?.dismiss()
        incomingRequestDialog = null
    }
    
    private fun updateUIForWebRTCState(state: WebRTCConnectionState) {
        when (state) {
            is WebRTCConnectionState.Connected -> {
                android.util.Log.d("DuoFragment", "WebRTC Connected to ${state.partnerDeviceName}")
                isWebRTCConnected = true
                showConnectedState()
                updateConnectionTypeSignature()
                animateDuoIconToTopLeft()
            }
            is WebRTCConnectionState.Disconnected -> {
                isWebRTCConnected = false
                // Only show welcome state if WiFi Direct is also disconnected
                if (duoViewModel.connectionState.value is DuoConnectionState.Disconnected) {
                    android.util.Log.d("DuoFragment", "WebRTC Disconnected, showing welcome state")
                    showWelcomeState()
                }
            }
            else -> {
                // For other states (Idle, CheckingPartner, CreatingOffer, etc.), don't change UI
            }
        }
    }

    private fun updateUIForConnectionState(state: DuoConnectionState) {
        android.util.Log.d("DuoFragment", "Updating UI for state: $state")
        when (state) {
            is DuoConnectionState.Disconnected,
            is DuoConnectionState.Searching,
            is DuoConnectionState.Error -> {
                isWifiDirectConnected = false
                // Only show welcome state if WebRTC is also disconnected
                if (!isWebRTCConnected) {
                    android.util.Log.d("DuoFragment", "Showing welcome state")
                    showWelcomeState()
                }
            }
            is DuoConnectionState.Connecting -> {
                // Keep showing welcome state while connecting
                android.util.Log.d("DuoFragment", "Showing welcome state (connecting)")
                if (!isWebRTCConnected) {
                    showWelcomeState()
                }
            }
            is DuoConnectionState.Connected -> {
                android.util.Log.d("DuoFragment", "Showing connected state, isHost=${state.isHost}")
                isWifiDirectConnected = true
                showConnectedState()
                updateConnectionTypeSignature()
                animateDuoIconToTopLeft()
            }
        }
    }
    
    /**
     * Update the connection type signature under the Duo title
     */
    private fun updateConnectionTypeSignature() {
        val connectionType = when {
            isWebRTCConnected -> getString(R.string.duo_connection_online)
            isWifiDirectConnected -> getString(R.string.duo_connection_wifi_direct)
            else -> ""
        }
        binding.tvConnectionType.text = connectionType
    }

    private fun showWelcomeState() {
        binding.welcomeContainer.visibility = View.VISIBLE
        binding.connectedContainer.visibility = View.GONE
        startWelcomeAnimation()
    }

    private fun showConnectedState() {
        welcomeAnimator?.cancel()
        binding.welcomeContainer.visibility = View.GONE
        binding.connectedContainer.visibility = View.VISIBLE
        
        // Trigger a resync when showing connected state to ensure songs are loaded
        android.util.Log.d("DuoFragment", "Connected state shown, triggering resync")
        duoViewModel.resyncLibrary()
    }

    private fun animateDuoIconToTopLeft() {
        // The icon is already in the top bar in connected state
        // Add a subtle scale animation for transition effect
        binding.ivDuoIcon.alpha = 0f
        binding.ivDuoIcon.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun updateSignalStrengthIcon(strength: SignalStrength) {
        val iconRes = when (strength) {
            SignalStrength.NONE -> R.drawable.ic_signal_wifi_0
            SignalStrength.WEAK -> R.drawable.ic_signal_wifi_1
            SignalStrength.FAIR -> R.drawable.ic_signal_wifi_2
            SignalStrength.GOOD -> R.drawable.ic_signal_wifi_3
            SignalStrength.EXCELLENT -> R.drawable.ic_signal_wifi_4
        }
        binding.ivSignalStrength.setImageResource(iconRes)
    }

    private fun syncLocalSongs() {
        // Observe local songs and sync with Duo
        musicViewModel.songs.observe(viewLifecycleOwner) { songs ->
            android.util.Log.d("DuoFragment", "Syncing ${songs.size} local songs to DuoViewModel")
            duoViewModel.setLocalSongs(songs)
        }
        
        // Observe local videos and sync with Duo
        musicViewModel.videos.observe(viewLifecycleOwner) { videos ->
            android.util.Log.d("DuoFragment", "Syncing ${videos.size} local videos to DuoViewModel")
            duoViewModel.setLocalVideos(videos)
        }
        
        // Observe local artists and sync with Duo
        musicViewModel.artists.observe(viewLifecycleOwner) { artists ->
            android.util.Log.d("DuoFragment", "Syncing ${artists.size} local artists to DuoViewModel")
            duoViewModel.setLocalArtists(artists)
        }
        
        // Observe local albums and sync with Duo
        musicViewModel.albums.observe(viewLifecycleOwner) { albums ->
            android.util.Log.d("DuoFragment", "Syncing ${albums.size} local albums to DuoViewModel")
            duoViewModel.setLocalAlbums(albums)
        }
        
        // Observe local folders and sync with Duo
        musicViewModel.folders.observe(viewLifecycleOwner) { folders ->
            android.util.Log.d("DuoFragment", "Syncing ${folders.size} local folders to DuoViewModel")
            duoViewModel.setLocalFolders(folders)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        welcomeAnimator?.cancel()
        dismissIncomingRequestDialog()
        _binding = null
    }

    companion object {
        fun newInstance() = DuoFragment()
    }
}
