package com.android.music.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.music.R
import com.android.music.auth.AuthManager
import com.android.music.data.model.Song
import com.android.music.data.repository.MusicRepository
import com.android.music.databinding.ActivityMainBinding
import com.android.music.databinding.LayoutPlayerBarBinding
import com.android.music.ui.fragment.DuoFragment
import com.android.music.ui.fragment.DownloadsTabFragment
import com.android.music.ui.fragment.ProfileFragment
import com.android.music.service.MusicService
import com.android.music.ui.adapter.TabPagerAdapter
import com.android.music.ui.fragment.PlayerSheetFragment
import com.android.music.ui.viewmodel.AuthViewModel
import com.android.music.ui.viewmodel.MusicViewModel
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var playerBarBinding: LayoutPlayerBarBinding
    private val viewModel: MusicViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val duoViewModel: DuoViewModel by viewModels()
    private lateinit var authManager: AuthManager
    private lateinit var tabPagerAdapter: TabPagerAdapter
    
    // ContentObserver to detect new media files
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            // Refresh media when new files are added
            viewModel.loadAllMedia()
        }
    }
    
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.let { authViewModel.signInWithGoogle(it) }
        } catch (e: ApiException) {
            Toast.makeText(this, "Sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicService.BROADCAST_PROGRESS -> {
                    val position = intent.getIntExtra(MusicService.EXTRA_POSITION, 0)
                    val duration = intent.getIntExtra(MusicService.EXTRA_DURATION, 0)
                    updateProgress(position, duration)
                }
                MusicService.BROADCAST_PLAYBACK_STATE -> {
                    val isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    val song = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(MusicService.EXTRA_SONG, Song::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(MusicService.EXTRA_SONG)
                    }
                    // Update playback state without triggering service restart
                    viewModel.updatePlaybackState(isPlaying, song)
                }
                MusicService.BROADCAST_NEXT -> {
                    // Service already changed the song, just update UI
                    viewModel.playNext()
                }
                MusicService.BROADCAST_PREVIOUS -> {
                    // Service already changed the song, just update UI
                    viewModel.playPrevious()
                }
                MusicService.BROADCAST_STOP -> {
                    // Player was stopped, clear UI state
                    viewModel.stopPlayback()
                }
                // Duo sync broadcasts from notification controls
                MusicService.BROADCAST_PAUSE -> {
                    if (isDuoConnected) {
                        android.util.Log.d("MainActivity", "Notification pause - syncing with Duo")
                        duoViewModel.syncPause()
                    }
                }
                MusicService.BROADCAST_RESUME -> {
                    if (isDuoConnected) {
                        android.util.Log.d("MainActivity", "Notification resume - syncing with Duo")
                        duoViewModel.syncResume()
                    }
                }
                MusicService.BROADCAST_SEEK -> {
                    if (isDuoConnected) {
                        val position = intent.getIntExtra(MusicService.EXTRA_POSITION, 0)
                        android.util.Log.d("MainActivity", "Notification seek - syncing with Duo: $position")
                        duoViewModel.syncSeek(position.toLong())
                    }
                }
                MusicService.BROADCAST_SONG_CHANGE -> {
                    if (isDuoConnected) {
                        val song = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(MusicService.EXTRA_SONG, Song::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(MusicService.EXTRA_SONG)
                        }
                        song?.let {
                            android.util.Log.d("MainActivity", "Notification song change - syncing with Duo: ${it.title}")
                            duoViewModel.syncSongChange(it)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViewModel()
        authManager = AuthManager(this)
        authViewModel.initialize(authManager)
        setupUI()
        setupDrawer()
        setupTabs()
        setupBottomNavigation()
        setupPlayerBar()
        setupSearch()
        observeViewModel()
        observeAuthState()
        registerReceivers()
        registerMediaObserver()
    }

    private fun registerMediaObserver() {
        // Register observer for audio files
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
        // Register observer for video files
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
    }

    private fun initializeViewModel() {
        val repository = MusicRepository(contentResolver)
        viewModel.initialize(repository)
    }

    private fun setupUI() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupDrawer() {
        // Setup sign-in button click listener - use navigationView.findViewById to search entire hierarchy
        val btnSignIn = binding.navigationView.findViewById<View>(R.id.btnSignIn)
        btnSignIn?.setOnClickListener {
            startGoogleSignIn()
        }
        
        // Setup menu item click listeners
        binding.navigationView.findViewById<View>(R.id.menuDownloads)?.setOnClickListener {
            openDownloads()
        }
        binding.navigationView.findViewById<View>(R.id.menuSettings)?.setOnClickListener {
            handleDrawerItemClick("Settings")
        }
        binding.navigationView.findViewById<View>(R.id.menuEqualizer)?.setOnClickListener {
            handleDrawerItemClick("Equalizer")
        }
        binding.navigationView.findViewById<View>(R.id.menuUserAgreement)?.setOnClickListener {
            handleDrawerItemClick("User Agreement")
        }
        binding.navigationView.findViewById<View>(R.id.menuPrivacyPolicy)?.setOnClickListener {
            handleDrawerItemClick("Privacy Policy")
        }
        binding.navigationView.findViewById<View>(R.id.menuUpdateAutomatically)?.setOnClickListener {
            handleDrawerItemClick("Update automatically")
        }
    }

    private fun openDownloads() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        
        // Switch to Downloads tab
        binding.bottomNavigation.selectedItemId = R.id.nav_downloads
        showDownloadsTab()
    }

    private fun handleDrawerItemClick(item: String) {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        
        when (item) {
            "Settings" -> openSettings()
            "Equalizer" -> openEqualizer()
            "User Agreement" -> openUserAgreement()
            "Privacy Policy" -> openPrivacyPolicy()
            else -> Toast.makeText(this, item, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSettings() {
        // Open Settings Activity
        val intent = Intent(this, com.android.music.settings.SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openEqualizer() {
        // Open Equalizer Activity
        val intent = Intent(this, com.android.music.equalizer.ui.EqualizerActivity::class.java)
        startActivity(intent)
    }

    private fun openUserAgreement() {
        com.android.music.legal.LegalDocumentActivity.startUserAgreement(this)
    }

    private fun openPrivacyPolicy() {
        com.android.music.legal.LegalDocumentActivity.startPrivacyPolicy(this)
    }

    private fun setupTabs() {
        tabPagerAdapter = TabPagerAdapter(this)
        binding.viewPager.adapter = tabPagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabPagerAdapter.getTabTitle(position)
        }.attach()
    }

    private fun setupBottomNavigation() {
        // Load Duo PNG icon from assets
        loadDuoIconFromAssets()
        
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_my_music -> {
                    showMyMusicTab()
                    true
                }
                R.id.nav_duo -> {
                    showDuoTab()
                    true
                }
                R.id.nav_downloads -> {
                    showDownloadsTab()
                    true
                }
                R.id.nav_browse -> {
                    showBrowseTab()
                    true
                }
                R.id.nav_profile -> {
                    showProfileTab()
                    true
                }
                else -> false
            }
        }
    }

    private fun showMyMusicTab() {
        binding.topBar.visibility = View.VISIBLE
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        binding.browseContainer.visibility = View.GONE
        binding.duoContainer.visibility = View.GONE
        binding.downloadsTabContainer.visibility = View.GONE
        binding.profileContainer.visibility = View.GONE
    }

    private fun showDuoTab() {
        binding.topBar.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.browseContainer.visibility = View.GONE
        binding.duoContainer.visibility = View.VISIBLE
        binding.downloadsTabContainer.visibility = View.GONE
        binding.profileContainer.visibility = View.GONE
        
        // Add DuoFragment if not already added
        if (supportFragmentManager.findFragmentById(R.id.duoContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.duoContainer, DuoFragment.newInstance())
                .commit()
        }
    }

    private fun showDownloadsTab() {
        binding.topBar.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.browseContainer.visibility = View.GONE
        binding.duoContainer.visibility = View.GONE
        binding.downloadsTabContainer.visibility = View.VISIBLE
        binding.profileContainer.visibility = View.GONE
        
        // Add DownloadsTabFragment if not already added
        if (supportFragmentManager.findFragmentById(R.id.downloadsTabContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.downloadsTabContainer, DownloadsTabFragment.newInstance())
                .commit()
        }
    }

    private fun showBrowseTab() {
        binding.topBar.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.browseContainer.visibility = View.VISIBLE
        binding.duoContainer.visibility = View.GONE
        binding.downloadsTabContainer.visibility = View.GONE
        binding.profileContainer.visibility = View.GONE
        
        // Add BrowseFragment if not already added
        if (supportFragmentManager.findFragmentById(R.id.browseContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.browseContainer, com.android.music.browse.ui.fragment.BrowseFragment.newInstance())
                .commit()
        }
    }

    private fun showProfileTab() {
        binding.topBar.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.browseContainer.visibility = View.GONE
        binding.duoContainer.visibility = View.GONE
        binding.downloadsTabContainer.visibility = View.GONE
        binding.profileContainer.visibility = View.VISIBLE
        
        // Add ProfileFragment if not already added
        if (supportFragmentManager.findFragmentById(R.id.profileContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.profileContainer, ProfileFragment.newInstance())
                .commit()
        }
    }

    private fun loadDuoIconFromAssets() {
        try {
            val duoMenuItem = binding.bottomNavigation.menu.findItem(R.id.nav_duo)
            Glide.with(this)
                .load("file:///android_asset/Duo.png")
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        // Apply stroke effect to make it bolder
                        val boldDrawable = applyStrokeToDrawable(resource)
                        duoMenuItem.icon = boldDrawable
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyStrokeToDrawable(drawable: android.graphics.drawable.Drawable): android.graphics.drawable.Drawable {
        val bitmap = android.graphics.Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        // Create a new bitmap with stroke effect
        val strokeBitmap = android.graphics.Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val strokeCanvas = android.graphics.Canvas(strokeBitmap)
        
        // Draw stroke (outline) by drawing the bitmap multiple times around the center
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }
        val strokeWidth = 2f
        
        for (i in -2..2) {
            for (j in -2..2) {
                if (i != 0 || j != 0) {
                    strokeCanvas.drawBitmap(bitmap, i.toFloat(), j.toFloat(), paint)
                }
            }
        }
        
        // Draw the original bitmap on top
        strokeCanvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return android.graphics.drawable.BitmapDrawable(resources, strokeBitmap)
    }

    // Track Duo state for player bar controls
    private var isDuoConnected = false
    
    private fun setupPlayerBar() {
        playerBarBinding = LayoutPlayerBarBinding.inflate(layoutInflater)
        binding.playerBarContainer.addView(playerBarBinding.root)

        // Click on player bar to open full player sheet
        playerBarBinding.root.setOnClickListener {
            openPlayerSheet()
        }
        
        // Also make the song info and thumbnail clickable
        playerBarBinding.cardThumbnail.setOnClickListener {
            openPlayerSheet()
        }
        playerBarBinding.playerSongInfo.setOnClickListener {
            openPlayerSheet()
        }

        playerBarBinding.btnPlayPause.setOnClickListener {
            val isCurrentlyPlaying = viewModel.isPlaying.value ?: false
            
            if (isDuoConnected) {
                // Send through DuoViewModel to sync with partner
                if (isCurrentlyPlaying) {
                    duoViewModel.pause()
                } else {
                    duoViewModel.resume()
                }
            }
            
            val action = if (isCurrentlyPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_RESUME
            startService(Intent(this, MusicService::class.java).apply { this.action = action })
        }

        playerBarBinding.btnNext.setOnClickListener {
            if (isDuoConnected) {
                // Send through DuoViewModel which will sync to partner
                duoViewModel.playNext()
                return@setOnClickListener
            }
            // Non-Duo mode - send directly to service
            startService(Intent(this, MusicService::class.java).apply { 
                action = MusicService.ACTION_NEXT 
            })
        }

        playerBarBinding.btnPrevious.setOnClickListener {
            if (isDuoConnected) {
                // Send through DuoViewModel which will sync to partner
                duoViewModel.playPrevious()
                return@setOnClickListener
            }
            // Non-Duo mode - send directly to service
            startService(Intent(this, MusicService::class.java).apply { 
                action = MusicService.ACTION_PREVIOUS 
            })
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        })
    }

    private fun observeViewModel() {
        // Sync songs with DuoViewModel for Duo feature
        viewModel.songs.observe(this) { songs ->
            duoViewModel.setLocalSongs(songs)
        }
        
        // Sync videos with DuoViewModel
        viewModel.videos.observe(this) { videos ->
            duoViewModel.setLocalVideos(videos)
        }
        
        // Sync artists with DuoViewModel
        viewModel.artists.observe(this) { artists ->
            duoViewModel.setLocalArtists(artists)
        }
        
        // Sync albums with DuoViewModel
        viewModel.albums.observe(this) { albums ->
            duoViewModel.setLocalAlbums(albums)
        }
        
        // Sync folders with DuoViewModel
        viewModel.folders.observe(this) { folders ->
            duoViewModel.setLocalFolders(folders)
        }
        
        // Observe current song for UI updates only
        viewModel.currentSong.observe(this) { song ->
            song?.let {
                playerBarBinding.tvPlayerTitle.text = it.title
                playerBarBinding.tvPlayerSubtitle.text = it.artist
                
                // Load actual album art with gradient fallback
                com.android.music.util.AlbumArtUtil.loadAlbumArtWithFallback(
                    com.bumptech.glide.Glide.with(this),
                    playerBarBinding.ivPlayerThumbnail,
                    it,
                    44
                )
                
                // Apply dynamic theming to player bar
                com.android.music.util.AlbumArtUtil.applyPlayerBarTheming(
                    playerBarBinding.root,
                    it
                )
            }
        }

        // Observe play song event to start service (one-time event)
        viewModel.playSongEvent.observe(this) { song ->
            song?.let {
                // Check if Duo is connected and song is not in common list
                if (isDuoConnected && !duoViewModel.isSongInCommonList(it)) {
                    // Show warning dialog
                    showNonCommonSongWarning(it)
                } else {
                    // Play the song normally
                    playSongNow(it)
                }
                viewModel.clearPlaySongEvent()
            }
        }

        viewModel.isPlaying.observe(this) { isPlaying ->
            playerBarBinding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        viewModel.showPlayerBar.observe(this) { show ->
            binding.playerBarContainer.visibility = if (show) View.VISIBLE else View.GONE
        }

        viewModel.navigateToSongsList.observe(this) { data ->
            data?.let { (title, songs) ->
                SongsListActivity.start(this, title, songs)
                viewModel.clearNavigation()
            }
        }
        
        // Observe Duo playback events
        lifecycleScope.launch {
            duoViewModel.playSongEvent.collect { (song, playlist) ->
                android.util.Log.d("MainActivity", "Duo playSongEvent: ${song.title}")
                val serviceIntent = Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_PLAY
                    putExtra(MusicService.EXTRA_SONG, song)
                    putParcelableArrayListExtra(MusicService.EXTRA_PLAYLIST, ArrayList(playlist))
                }
                startForegroundService(serviceIntent)
                
                // Also show the player bar
                binding.playerBarContainer.visibility = View.VISIBLE
            }
        }
        
        // Observe Duo pause events
        lifecycleScope.launch {
            duoViewModel.pauseEvent.collect {
                android.util.Log.d("MainActivity", "Duo pauseEvent received")
                startService(Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_PAUSE
                })
            }
        }
        
        // Observe Duo resume events
        lifecycleScope.launch {
            duoViewModel.resumeEvent.collect {
                android.util.Log.d("MainActivity", "Duo resumeEvent received")
                startService(Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_RESUME
                })
            }
        }
        
        // Observe Duo seek events
        lifecycleScope.launch {
            duoViewModel.seekEvent.collect { position ->
                android.util.Log.d("MainActivity", "Duo seekEvent received: $position")
                startService(Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_SEEK
                    putExtra(MusicService.EXTRA_POSITION, position.toInt())
                })
            }
        }
        
        // Observe Duo connection state for player bar controls (WiFi Direct)
        lifecycleScope.launch {
            duoViewModel.connectionState.collect { state ->
                val wifiDirectConnected = state is com.android.music.duo.data.model.DuoConnectionState.Connected
                isDuoConnected = wifiDirectConnected || duoViewModel.webRTCConnectionState.value is com.android.music.duo.webrtc.model.WebRTCConnectionState.Connected
                android.util.Log.d("MainActivity", "Duo WiFi Direct state: $state, isDuoConnected=$isDuoConnected")
            }
        }
        
        // Observe WebRTC connection state for player bar controls
        lifecycleScope.launch {
            duoViewModel.webRTCConnectionState.collect { state ->
                val webRTCConnected = state is com.android.music.duo.webrtc.model.WebRTCConnectionState.Connected
                isDuoConnected = webRTCConnected || duoViewModel.connectionState.value is com.android.music.duo.data.model.DuoConnectionState.Connected
                android.util.Log.d("MainActivity", "Duo WebRTC state: $state, isDuoConnected=$isDuoConnected")
            }
        }
    }
    
    private fun observeAuthState() {
        authViewModel.currentUser.observe(this) { user ->
            updateDrawerHeader(user)
        }
        
        authViewModel.signInState.observe(this) { state ->
            when (state) {
                is AuthViewModel.SignInState.Loading -> {
                    // Show loading if needed
                }
                is AuthViewModel.SignInState.Success -> {
                    Toast.makeText(this, "Welcome ${state.user.displayName}!", Toast.LENGTH_SHORT).show()
                }
                is AuthViewModel.SignInState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        authViewModel.showSignInPrompt.observe(this) { show ->
            if (show) {
                showSignInPromptDialog()
            }
        }
    }
    
    private fun updateDrawerHeader(user: com.android.music.data.model.User?) {
        val ivProfile = binding.navigationView.findViewById<ImageView>(R.id.ivProfilePicture)
        val tvName = binding.navigationView.findViewById<TextView>(R.id.tvUserName)
        val btnSignIn = binding.navigationView.findViewById<View>(R.id.btnSignIn)
        
        if (user != null) {
            // User signed in
            tvName?.visibility = View.VISIBLE
            tvName?.text = user.displayName ?: "User"
            btnSignIn?.visibility = View.GONE
            
            // Load profile picture
            user.photoUrl?.let { photoUrl ->
                ivProfile?.let {
                    Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_music_note)
                        .circleCrop()
                        .into(it)
                }
            }
        } else {
            // User not signed in
            tvName?.visibility = View.GONE
            btnSignIn?.visibility = View.VISIBLE
            ivProfile?.setImageResource(R.drawable.ic_music_note)
        }
    }
    
    private fun startGoogleSignIn() {
        Toast.makeText(this, "Starting Google Sign-In...", Toast.LENGTH_SHORT).show()
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        val signInIntent = authManager.getGoogleSignInClient().signInIntent
        googleSignInLauncher.launch(signInIntent)
    }
    
    private fun showSignInPromptDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sign_in_prompt, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialogView.findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            authViewModel.dismissSignInPrompt()
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.btnOpenDrawer).setOnClickListener {
            authViewModel.dismissSignInPrompt()
            dialog.dismiss()
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        
        dialog.show()
    }

    private fun updateProgress(position: Int, duration: Int) {
        playerBarBinding.progressBar.max = duration
        playerBarBinding.progressBar.progress = position
        viewModel.updateProgress(position, duration)
    }
    
    /**
     * Show warning dialog when trying to play a song not in common list while in Duo mode
     */
    private fun showNonCommonSongWarning(song: Song) {
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.duo_song_not_common_title)
            .setMessage(R.string.duo_song_not_common_message)
            .setIcon(R.drawable.ic_smartphone)
            .setPositiveButton(R.string.duo_play_anyway) { _, _ ->
                // Disconnect Duo and play the song
                duoViewModel.disconnect()
                playSongNow(song)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    /**
     * Play a song immediately without checks
     */
    private fun playSongNow(song: Song) {
        // Get the current playlist from ViewModel
        val playlist = viewModel.getCurrentPlaylist().ifEmpty { viewModel.songs.value ?: listOf(song) }
        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            putExtra(MusicService.EXTRA_SONG, song)
            putParcelableArrayListExtra(MusicService.EXTRA_PLAYLIST, ArrayList(playlist))
        }
        startForegroundService(serviceIntent)
    }

    private fun openPlayerSheet() {
        val playerSheet = PlayerSheetFragment.newInstance()
        playerSheet.show(supportFragmentManager, PlayerSheetFragment.TAG)
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(MusicService.BROADCAST_PROGRESS)
            addAction(MusicService.BROADCAST_PLAYBACK_STATE)
            addAction(MusicService.BROADCAST_NEXT)
            addAction(MusicService.BROADCAST_PREVIOUS)
            addAction(MusicService.BROADCAST_STOP)
            addAction(MusicService.BROADCAST_PAUSE)
            addAction(MusicService.BROADCAST_RESUME)
            addAction(MusicService.BROADCAST_SEEK)
            addAction(MusicService.BROADCAST_SONG_CHANGE)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(playbackReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playbackReceiver)
        contentResolver.unregisterContentObserver(mediaObserver)
    }

    override fun onResume() {
        super.onResume()
        // Refresh media list when returning to app
        viewModel.loadAllMedia()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            // Check if Browse tab is active and has back stack
            val browseFragment = supportFragmentManager.findFragmentById(R.id.browseContainer) 
                as? com.android.music.browse.ui.fragment.BrowseFragment
            
            if (browseFragment != null && binding.browseContainer.visibility == View.VISIBLE) {
                // Check if BrowseFragment has child fragments in back stack
                if (browseFragment.childFragmentManager.backStackEntryCount > 0) {
                    browseFragment.childFragmentManager.popBackStack()
                    return
                }
            }
            
            super.onBackPressed()
        }
    }
}
