package com.android.music.duo.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.music.data.model.Song
import com.android.music.data.model.Video
import com.android.music.data.model.Artist
import com.android.music.data.model.Album
import com.android.music.data.model.Folder
import com.android.music.duo.data.model.*
import com.android.music.duo.data.repository.DuoRepository
import com.android.music.duo.webrtc.SignalingManager
import com.android.music.duo.webrtc.WebRTCRepository
import com.android.music.duo.webrtc.model.WebRTCConnectionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DuoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DuoRepository(application)
    private val webRTCRepository = WebRTCRepository(application)
    
    // Flag to prevent feedback loops when processing remote commands
    private var isProcessingRemoteCommand = false

    // WiFi Direct connection state
    val connectionState: StateFlow<DuoConnectionState> = repository.connectionState
    val isHost: StateFlow<Boolean> = repository.isHost
    private val wifiDirectSignalStrength: StateFlow<SignalStrength> = repository.signalStrength
    val discoveredDevices: StateFlow<List<DuoDevice>> = repository.discoveredDevices

    // WebRTC connection state
    val webRTCConnectionState: StateFlow<WebRTCConnectionState> = webRTCRepository.connectionState
    val incomingWebRTCOffer: StateFlow<SignalingManager.IncomingOffer?> = webRTCRepository.incomingRequest
    
    // Combined signal strength from both WiFi Direct and WebRTC
    private val _combinedSignalStrength = MutableStateFlow(SignalStrength.NONE)
    val signalStrength: StateFlow<SignalStrength> = _combinedSignalStrength.asStateFlow()

    // Songs - combine common songs from both WiFi Direct and WebRTC
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    
    // Combined common songs from both connection types
    private val _combinedCommonSongs = MutableStateFlow<List<Song>>(emptyList())
    val commonSongs: StateFlow<List<Song>> = _combinedCommonSongs.asStateFlow()

    private val _filteredSongs = MutableStateFlow<List<Song>>(emptyList())
    val filteredSongs: StateFlow<List<Song>> = _filteredSongs.asStateFlow()
    
    // Videos - derived from common songs
    private val _commonVideos = MutableStateFlow<List<Video>>(emptyList())

    private val _filteredVideos = MutableStateFlow<List<Video>>(emptyList())
    val filteredVideos: StateFlow<List<Video>> = _filteredVideos.asStateFlow()
    
    // Artists - derived from common songs
    private val _commonArtists = MutableStateFlow<List<Artist>>(emptyList())

    private val _filteredArtists = MutableStateFlow<List<Artist>>(emptyList())
    val filteredArtists: StateFlow<List<Artist>> = _filteredArtists.asStateFlow()
    
    // Albums - derived from common songs
    private val _commonAlbums = MutableStateFlow<List<Album>>(emptyList())

    private val _filteredAlbums = MutableStateFlow<List<Album>>(emptyList())
    val filteredAlbums: StateFlow<List<Album>> = _filteredAlbums.asStateFlow()
    
    // Folders - derived from common songs (folders containing common songs)
    private val _commonFolders = MutableStateFlow<List<Folder>>(emptyList())

    private val _filteredFolders = MutableStateFlow<List<Folder>>(emptyList())
    val filteredFolders: StateFlow<List<Folder>> = _filteredFolders.asStateFlow()
    
    // Local media for reference
    private val _localVideos = MutableStateFlow<List<Video>>(emptyList())
    private val _localArtists = MutableStateFlow<List<Artist>>(emptyList())
    private val _localAlbums = MutableStateFlow<List<Album>>(emptyList())
    private val _localFolders = MutableStateFlow<List<Folder>>(emptyList())

    // Search
    private val _searchQuery = MutableStateFlow("")

    // Sort
    private val _sortOption = MutableStateFlow(DuoSortOption.NAME)

    // Current playing
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Playback controls
    private val _shuffleEnabled = MutableStateFlow(false)

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)

    // Events
    private val _showConnectionSheet = MutableStateFlow(false)

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()
    
    // Permission request event
    private val _requestAudioPermission = MutableSharedFlow<Unit>()
    val requestAudioPermission: SharedFlow<Unit> = _requestAudioPermission.asSharedFlow()
    
    // Playback event - emits song to play (observed by Activity to start MusicService)
    private val _playSongEvent = MutableSharedFlow<Pair<Song, List<Song>>>()
    val playSongEvent: SharedFlow<Pair<Song, List<Song>>> = _playSongEvent.asSharedFlow()
    
    // Pause event
    private val _pauseEvent = MutableSharedFlow<Unit>()
    val pauseEvent: SharedFlow<Unit> = _pauseEvent.asSharedFlow()
    
    // Resume event
    private val _resumeEvent = MutableSharedFlow<Unit>()
    val resumeEvent: SharedFlow<Unit> = _resumeEvent.asSharedFlow()
    
    // Seek event
    private val _seekEvent = MutableSharedFlow<Long>()
    val seekEvent: SharedFlow<Long> = _seekEvent.asSharedFlow()

    // Incoming commands (from WiFi Direct)
    val incomingCommand: SharedFlow<DuoCommand> = repository.incomingCommand

    // Device info
    val deviceName: String = Build.MODEL

    // Chat state
    private val _chatMessages = MutableStateFlow<List<com.android.music.duo.chat.model.ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<com.android.music.duo.chat.model.ChatMessage>> = _chatMessages.asStateFlow()
    
    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping: StateFlow<Boolean> = _isPartnerTyping.asStateFlow()
    
    private val _hasUnreadMessages = MutableStateFlow(false)
    val hasUnreadMessages: StateFlow<Boolean> = _hasUnreadMessages.asStateFlow()
    
    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()
    
    // Vibrator for message notifications
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(android.os.VibratorManager::class.java)
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(android.os.Vibrator::class.java)
    }
    
    // Chat open state to track if user is viewing chat
    private var isChatOpen = false
    
    // Connection type text for UI
    private val _connectionTypeText = MutableStateFlow("")
    val connectionTypeText: StateFlow<String> = _connectionTypeText.asStateFlow()
    
    // Combined connected state
    val isConnectedFlow: StateFlow<Boolean> = combine(
        connectionState,
        webRTCConnectionState
    ) { wifiState, webrtcState ->
        wifiState is DuoConnectionState.Connected || webrtcState is WebRTCConnectionState.Connected
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    // Typing debounce
    private var typingJob: kotlinx.coroutines.Job? = null

    init {
        initializeWebRTC()
        observeCommonSongsFromBothSources()
        observeIncomingCommands()
        observeWebRTCEvents()
        observeSignalStrength()
        observeConnectionType()
        observeChatMessages()
    }
    
    /**
     * Observe connection type and update text for UI
     */
    private fun observeConnectionType() {
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is DuoConnectionState.Connected) {
                    _connectionTypeText.value = "WiFi Direct"
                }
            }
        }
        
        viewModelScope.launch {
            webRTCConnectionState.collect { state ->
                if (state is WebRTCConnectionState.Connected) {
                    _connectionTypeText.value = "Online WebRTC"
                }
            }
        }
    }
    
    /**
     * Observe chat messages from both WiFi Direct and WebRTC
     */
    private fun observeChatMessages() {
        // Observe WiFi Direct incoming messages for chat
        viewModelScope.launch {
            repository.incomingChatMessage.collect { payload ->
                handleIncomingChatMessage(payload)
            }
        }
        
        // Observe WebRTC incoming messages for chat
        viewModelScope.launch {
            webRTCRepository.incomingChatMessage.collect { payload ->
                handleIncomingChatMessage(payload)
            }
        }
        
        // Observe typing indicators
        viewModelScope.launch {
            repository.isPartnerTyping.collect { typing ->
                _isPartnerTyping.value = typing
            }
        }
        
        viewModelScope.launch {
            webRTCRepository.isPartnerTyping.collect { typing ->
                _isPartnerTyping.value = typing
            }
        }
        
        // Observe message acknowledgments from WiFi Direct
        viewModelScope.launch {
            repository.messageDelivered.collect { messageId ->
                handleMessageDelivered(messageId)
            }
        }
        
        viewModelScope.launch {
            repository.messageRead.collect { messageId ->
                handleMessageRead(messageId)
            }
        }
        
        // Observe message acknowledgments from WebRTC
        viewModelScope.launch {
            webRTCRepository.messageDelivered.collect { messageId ->
                handleMessageDelivered(messageId)
            }
        }
        
        viewModelScope.launch {
            webRTCRepository.messageRead.collect { messageId ->
                handleMessageRead(messageId)
            }
        }
        
        // Observe voice messages
        viewModelScope.launch {
            repository.incomingVoiceMessage.collect { payload ->
                handleIncomingVoiceMessage(payload)
            }
        }
        
        viewModelScope.launch {
            webRTCRepository.incomingVoiceMessage.collect { payload ->
                handleIncomingVoiceMessage(payload)
            }
        }
    }
    
    /**
     * Handle incoming chat message
     */
    private fun handleIncomingChatMessage(payload: ChatMessagePayload) {
        android.util.Log.d("DuoViewModel", "Received chat message: ${payload.messageId} from ${payload.senderName}")
        val message = com.android.music.duo.chat.model.ChatMessage(
            id = payload.messageId,
            text = payload.text,
            senderName = payload.senderName,
            isFromMe = false,
            status = com.android.music.duo.chat.model.MessageStatus.DELIVERED
        )
        _chatMessages.value += message
        _isPartnerTyping.value = false
        
        // Show unread badge if chat is not open
        android.util.Log.d("DuoViewModel", "isChatOpen: $isChatOpen")
        if (!isChatOpen) {
            android.util.Log.d("DuoViewModel", "Setting hasUnreadMessages to true and vibrating")
            _hasUnreadMessages.value = true
            // Vibrate device
            vibrateDevice()
        } else {
            android.util.Log.d("DuoViewModel", "Chat is open, not showing badge")
        }
        
        // Send delivery acknowledgment
        android.util.Log.d("DuoViewModel", "Sending delivery ack for message: ${payload.messageId}")
        viewModelScope.launch {
            sendMessageDelivered(payload.messageId)
        }
    }
    
    /**
     * Handle incoming voice message
     */
    private fun handleIncomingVoiceMessage(payload: VoiceMessagePayload) {
        val message = com.android.music.duo.chat.model.ChatMessage(
            id = payload.messageId,
            text = "",
            senderName = payload.senderName,
            isFromMe = false,
            status = com.android.music.duo.chat.model.MessageStatus.DELIVERED,
            type = com.android.music.duo.chat.model.MessageType.VOICE,
            voiceDuration = payload.duration,
            voiceData = android.util.Base64.decode(payload.audioBase64, android.util.Base64.DEFAULT)
        )
        _chatMessages.value += message
        _isPartnerTyping.value = false
        
        // Show unread badge if chat is not open
        if (!isChatOpen) {
            _hasUnreadMessages.value = true
            vibrateDevice()
        }
        
        // Send delivery acknowledgment
        viewModelScope.launch {
            sendMessageDelivered(payload.messageId)
        }
    }
    
    /**
     * Handle message delivered acknowledgment
     */
    private fun handleMessageDelivered(messageId: String) {
        android.util.Log.d("DuoViewModel", "Received DELIVERED ack for message: $messageId")
        val updatedMessages = _chatMessages.value.map { msg ->
            if (msg.id == messageId && msg.isFromMe) {
                android.util.Log.d("DuoViewModel", "Updating message $messageId from ${msg.status} to DELIVERED")
                msg.copy(status = com.android.music.duo.chat.model.MessageStatus.DELIVERED)
            } else msg
        }
        _chatMessages.value = updatedMessages
    }
    
    /**
     * Handle message read acknowledgment
     */
    private fun handleMessageRead(messageId: String) {
        android.util.Log.d("DuoViewModel", "Received READ ack for message: $messageId")
        val updatedMessages = _chatMessages.value.map { msg ->
            if (msg.id == messageId && msg.isFromMe) {
                android.util.Log.d("DuoViewModel", "Updating message $messageId from ${msg.status} to READ")
                msg.copy(status = com.android.music.duo.chat.model.MessageStatus.READ)
            } else msg
        }
        _chatMessages.value = updatedMessages
    }
    
    /**
     * Vibrate device for notification
     */
    private fun vibrateDevice() {
        android.util.Log.d("DuoViewModel", "Attempting to vibrate device")
        try {
            if (vibrator == null) {
                android.util.Log.e("DuoViewModel", "Vibrator is null")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                android.util.Log.d("DuoViewModel", "Vibration triggered (API >= O)")
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
                android.util.Log.d("DuoViewModel", "Vibration triggered (API < O)")
            }
        } catch (e: Exception) {
            android.util.Log.e("DuoViewModel", "Failed to vibrate: ${e.message}", e)
        }
    }
    
    /**
     * Send message delivered acknowledgment
     */
    private suspend fun sendMessageDelivered(messageId: String) {
        android.util.Log.d("DuoViewModel", "Sending MESSAGE_DELIVERED for: $messageId")
        val duoMessage = DuoMessage.createMessageDelivered(messageId)
        val success = if (webRTCRepository.isConnected()) {
            webRTCRepository.sendMessage(duoMessage)
        } else {
            repository.sendMessage(duoMessage)
        }
        android.util.Log.d("DuoViewModel", "MESSAGE_DELIVERED send result: $success")
    }
    
    /**
     * Mark all messages as read and notify partner
     */
    fun markMessagesAsRead() {
        _hasUnreadMessages.value = false
        
        // Send read acknowledgment for all unread messages from partner
        viewModelScope.launch {
            _chatMessages.value
                .filter { !it.isFromMe && it.status != com.android.music.duo.chat.model.MessageStatus.READ }
                .forEach { msg ->
                    val duoMessage = DuoMessage.createMessageRead(msg.id)
                    if (webRTCRepository.isConnected()) {
                        webRTCRepository.sendMessage(duoMessage)
                    } else {
                        repository.sendMessage(duoMessage)
                    }
                }
        }
    }
    
    /**
     * Called when chat is opened
     */
    fun onChatOpened() {
        android.util.Log.d("DuoViewModel", "Chat opened")
        isChatOpen = true
        _hasUnreadMessages.value = false
    }
    
    /**
     * Called when chat is closed
     */
    fun onChatClosed() {
        android.util.Log.d("DuoViewModel", "Chat closed")
        isChatOpen = false
    }
    
    /**
     * Send a chat message
     */
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        
        val message = com.android.music.duo.chat.model.ChatMessage(
            text = text.trim(),
            senderName = deviceName,
            isFromMe = true,
            status = com.android.music.duo.chat.model.MessageStatus.SENDING
        )
        
        val messageId = message.id
        _chatMessages.value += message
        android.util.Log.d("DuoViewModel", "Added message with id: $messageId, status: SENDING")
        
        viewModelScope.launch {
            try {
                val duoMessage = DuoMessage.createChatMessage(messageId, message.text, message.senderName)
                val isWebRTCConnected = webRTCRepository.isConnected()
                val isWifiDirectConnected = connectionState.value is DuoConnectionState.Connected
                
                android.util.Log.d("DuoViewModel", "Connection status - WebRTC: $isWebRTCConnected, WiFi Direct: $isWifiDirectConnected")
                
                val success = when {
                    isWebRTCConnected -> {
                        android.util.Log.d("DuoViewModel", "Sending via WebRTC")
                        webRTCRepository.sendChatMessage(duoMessage)
                    }
                    isWifiDirectConnected -> {
                        android.util.Log.d("DuoViewModel", "Sending via WiFi Direct")
                        repository.sendChatMessage(duoMessage)
                    }
                    else -> {
                        android.util.Log.e("DuoViewModel", "No connection available!")
                        false
                    }
                }
                
                android.util.Log.d("DuoViewModel", "Message send result: $success for id: $messageId")
                
                // Update message status
                val newStatus = if (success) {
                    com.android.music.duo.chat.model.MessageStatus.SENT
                } else {
                    com.android.music.duo.chat.model.MessageStatus.FAILED
                }
                
                android.util.Log.d("DuoViewModel", "Updating message $messageId to status: $newStatus")
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == messageId) msg.copy(status = newStatus) else msg
                }
            } catch (e: Exception) {
                android.util.Log.e("DuoViewModel", "Error sending message", e)
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == messageId) msg.copy(status = com.android.music.duo.chat.model.MessageStatus.FAILED) else msg
                }
            }
        }
        
        // Stop typing indicator
        stopTypingIndicator()
    }
    
    /**
     * Notify partner that we're typing
     */
    fun notifyTyping() {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendTypingStart()
            } else {
                repository.sendTypingStart()
            }
            
            // Auto-stop typing after 3 seconds
            kotlinx.coroutines.delay(3000)
            stopTypingIndicator()
        }
    }
    
    /**
     * Stop typing indicator
     */
    private fun stopTypingIndicator() {
        typingJob?.cancel()
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendTypingStop()
            } else {
                repository.sendTypingStop()
            }
        }
    }
    
    /**
     * Clear chat messages (on disconnect)
     */
    private fun clearChatMessages() {
        _chatMessages.value = emptyList()
        _isPartnerTyping.value = false
        _hasUnreadMessages.value = false
    }
    
    /**
     * Toggle voice recording
     */
    fun toggleVoiceRecording() {
        if (_isRecordingVoice.value) {
            // Stop recording and send voice message
            stopVoiceRecording()
        } else {
            // Check permission first
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.d("DuoViewModel", "RECORD_AUDIO permission not granted, requesting...")
                    // Emit event to request permission
                    viewModelScope.launch {
                        _requestAudioPermission.emit(Unit)
                    }
                    return
                }
            }
            // Start recording
            startVoiceRecording()
        }
    }
    
    /**
     * Called after permission is granted to start recording
     */
    fun onAudioPermissionGranted() {
        android.util.Log.d("DuoViewModel", "Audio permission granted, starting recording")
        startVoiceRecording()
    }
    
    // Voice recording
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var voiceRecordingFile: java.io.File? = null
    private var recordingStartTime: Long = 0L
    
    private fun startVoiceRecording() {
        try {
            // Clean up any existing recorder first
            cleanupRecording()
            
            val context = getApplication<Application>()
            voiceRecordingFile = java.io.File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(voiceRecordingFile?.absolutePath)
                prepare()
                start()
            }
            
            recordingStartTime = System.currentTimeMillis()
            _isRecordingVoice.value = true
            android.util.Log.d("DuoViewModel", "Voice recording started")
            
            viewModelScope.launch {
                _toastMessage.emit("Recording...")
            }
        } catch (e: Exception) {
            android.util.Log.e("DuoViewModel", "Failed to start recording: ${e.message}", e)
            viewModelScope.launch {
                _toastMessage.emit("Failed to start recording. Please check microphone permissions.")
            }
            cleanupRecording()
        }
    }
    
    private fun stopVoiceRecording() {
        try {
            val duration = System.currentTimeMillis() - recordingStartTime
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _isRecordingVoice.value = false
            
            android.util.Log.d("DuoViewModel", "Voice recording stopped, duration: $duration ms")
            
            // Read the recorded file and send
            voiceRecordingFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    val audioData = file.readBytes()
                    android.util.Log.d("DuoViewModel", "Sending voice message, size: ${audioData.size} bytes")
                    sendVoiceMessage(audioData, duration)
                    file.delete()
                } else {
                    android.util.Log.e("DuoViewModel", "Recording file is empty or doesn't exist")
                    viewModelScope.launch {
                        _toastMessage.emit("Recording failed")
                    }
                }
            }
            voiceRecordingFile = null
            
        } catch (e: Exception) {
            android.util.Log.e("DuoViewModel", "Failed to stop recording", e)
            viewModelScope.launch {
                _toastMessage.emit("Failed to stop recording: ${e.message}")
            }
            cleanupRecording()
        }
    }
    
    private fun cleanupRecording() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
            // Ignore
        }
        mediaRecorder = null
        _isRecordingVoice.value = false
        voiceRecordingFile?.delete()
        voiceRecordingFile = null
    }
    
    /**
     * Send a voice message
     */
    fun sendVoiceMessage(audioData: ByteArray, duration: Long) {
        val message = com.android.music.duo.chat.model.ChatMessage(
            text = "",
            senderName = deviceName,
            isFromMe = true,
            status = com.android.music.duo.chat.model.MessageStatus.SENDING,
            type = com.android.music.duo.chat.model.MessageType.VOICE,
            voiceDuration = duration,
            voiceData = audioData
        )

        _chatMessages.value += message
        
        viewModelScope.launch {
            val audioBase64 = android.util.Base64.encodeToString(audioData, android.util.Base64.DEFAULT)
            val duoMessage = DuoMessage.createVoiceMessage(message.id, message.senderName, duration, audioBase64)
            val success = if (webRTCRepository.isConnected()) {
                webRTCRepository.sendMessage(duoMessage)
            } else {
                repository.sendMessage(duoMessage)
            }
            
            val newStatus = if (success) {
                com.android.music.duo.chat.model.MessageStatus.SENT
            } else {
                com.android.music.duo.chat.model.MessageStatus.FAILED
            }
            
            _chatMessages.value = _chatMessages.value.map { msg ->
                if (msg.id == message.id) msg.copy(status = newStatus) else msg
            }
        }
    }
    
    /**
     * Observe signal strength from both WiFi Direct and WebRTC
     * and update the combined signal strength
     */
    private fun observeSignalStrength() {
        // Observe WiFi Direct signal strength
        viewModelScope.launch {
            wifiDirectSignalStrength.collect { strength ->
                // Only use WiFi Direct signal if connected via WiFi Direct
                if (connectionState.value is DuoConnectionState.Connected) {
                    _combinedSignalStrength.value = strength
                }
            }
        }
        
        // Observe WebRTC connection quality and convert to SignalStrength
        viewModelScope.launch {
            webRTCRepository.connectionQuality.collect { quality ->
                // Only use WebRTC quality if connected via WebRTC
                if (webRTCRepository.isConnected()) {
                    _combinedSignalStrength.value = qualityToSignalStrength(quality)
                }
            }
        }
        
        // Reset signal strength when disconnected
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is DuoConnectionState.Disconnected && !webRTCRepository.isConnected()) {
                    _combinedSignalStrength.value = SignalStrength.NONE
                }
            }
        }
        
        viewModelScope.launch {
            webRTCConnectionState.collect { state ->
                if (state is WebRTCConnectionState.Disconnected || state is WebRTCConnectionState.Idle) {
                    if (connectionState.value !is DuoConnectionState.Connected) {
                        _combinedSignalStrength.value = SignalStrength.NONE
                    }
                }
            }
        }
    }
    
    /**
     * Convert WebRTC quality score (0-100) to SignalStrength enum
     */
    private fun qualityToSignalStrength(quality: Int): SignalStrength {
        return when {
            quality >= 80 -> SignalStrength.EXCELLENT
            quality >= 60 -> SignalStrength.GOOD
            quality >= 40 -> SignalStrength.FAIR
            quality >= 20 -> SignalStrength.WEAK
            else -> SignalStrength.NONE
        }
    }
    
    /**
     * Observe common songs from both WiFi Direct and WebRTC repositories
     * and update the filtered songs accordingly
     */
    private fun observeCommonSongsFromBothSources() {
        // Observe common songs from WiFi Direct repository
        viewModelScope.launch {
            repository.commonSongs.collect { songs ->
                android.util.Log.d("DuoViewModel", "WiFi Direct commonSongs updated: ${songs.size}")
                if (songs.isNotEmpty()) {
                    _combinedCommonSongs.value = songs
                    updateFilteredSongs(songs)
                    updateAllCommonMedia()
                    _toastMessage.emit("Found ${songs.size} common songs!")
                }
            }
        }
        
        // Observe common songs from WebRTC repository
        viewModelScope.launch {
            webRTCRepository.commonSongs.collect { songs ->
                android.util.Log.d("DuoViewModel", "WebRTC commonSongs updated: ${songs.size}")
                if (songs.isNotEmpty()) {
                    _combinedCommonSongs.value = songs
                    updateFilteredSongs(songs)
                    updateAllCommonMedia()
                    _toastMessage.emit("Found ${songs.size} common songs!")
                }
            }
        }
        
        // Observe search and sort changes
        viewModelScope.launch {
            combine(
                _combinedCommonSongs,
                _searchQuery,
                _sortOption
            ) { songs, query, sort ->
                android.util.Log.d("DuoViewModel", "Filter/sort changed: ${songs.size} songs, query='$query', sort=$sort")
                filterAndSortSongs(songs, query, sort)
            }.collect { filtered ->
                android.util.Log.d("DuoViewModel", "Setting filteredSongs: ${filtered.size}")
                _filteredSongs.value = filtered
            }
        }
    }
    
    /**
     * Update all common media types (videos, artists, albums, folders)
     * Called when common songs change or when local media is set
     */
    private fun updateAllCommonMedia() {
        android.util.Log.d("DuoViewModel", "updateAllCommonMedia called - commonSongs: ${_combinedCommonSongs.value.size}, localArtists: ${_localArtists.value.size}, localAlbums: ${_localAlbums.value.size}, localFolders: ${_localFolders.value.size}, localVideos: ${_localVideos.value.size}")
        updateCommonVideos()
        updateCommonArtists()
        updateCommonAlbums()
        updateCommonFolders()
    }
    
    private fun initializeWebRTC() {
        viewModelScope.launch {
            try {
                val duoId = webRTCRepository.initialize()
                android.util.Log.d("DuoViewModel", "WebRTC initialized with Duo ID: $duoId")
            } catch (e: Exception) {
                android.util.Log.e("DuoViewModel", "Failed to initialize WebRTC", e)
            }
        }
    }
    
    /**
     * Get my Duo ID
     */
    suspend fun getMyDuoId(): String {
        return webRTCRepository.getMyDuoId() ?: webRTCRepository.initialize()
    }
    
    /**
     * Format last seen timestamp
     */
    fun formatLastSeen(timestamp: Long): String {
        return webRTCRepository.formatLastSeen(timestamp)
    }
    
    /**
     * Connect to partner by Duo ID (WebRTC)
     */
    fun connectByDuoId(partnerId: String) {
        viewModelScope.launch {
            webRTCRepository.connectToPartner(partnerId)
        }
    }
    
    /**
     * Request notification when partner comes online
     */
    fun requestNotifyWhenOnline(partnerId: String) {
        viewModelScope.launch {
            val success = webRTCRepository.requestNotifyWhenOnline(partnerId)
            if (success) {
                _toastMessage.emit("You'll be notified when they come online")
            } else {
                _toastMessage.emit("Failed to set notification")
            }
        }
    }
    
    /**
     * Accept incoming WebRTC connection offer
     */
    fun acceptIncomingOffer(offer: SignalingManager.IncomingOffer) {
        viewModelScope.launch {
            webRTCRepository.acceptIncomingRequest(offer)
        }
    }
    
    /**
     * Reject incoming WebRTC connection offer
     */
    fun rejectIncomingOffer(offer: SignalingManager.IncomingOffer) {
        webRTCRepository.rejectIncomingRequest(offer)
    }
    
    private fun observeWebRTCEvents() {
        // Observe WebRTC events
        viewModelScope.launch {
            webRTCRepository.events.collect { event ->
                when (event) {
                    is WebRTCRepository.WebRTCEvent.Error -> {
                        _toastMessage.emit(event.message)
                    }
                    is WebRTCRepository.WebRTCEvent.PartnerOffline -> {
                        // UI will handle this via state
                    }
                    is WebRTCRepository.WebRTCEvent.Connected -> {
                        _toastMessage.emit("Connected via Internet!")
                    }
                    is WebRTCRepository.WebRTCEvent.Disconnected -> {
                        _toastMessage.emit("Disconnected")
                    }
                    is WebRTCRepository.WebRTCEvent.IncomingRequest -> {
                        _toastMessage.emit("${event.fromDeviceName} wants to connect")
                    }
                }
            }
        }
        
        // Observe WebRTC incoming commands
        viewModelScope.launch {
            webRTCRepository.incomingCommand.collect { command: DuoCommand ->
                handleIncomingCommand(command)
            }
        }
    }

    private fun updateFilteredSongs(songs: List<Song>) {
        val filtered = filterAndSortSongs(songs, _searchQuery.value, _sortOption.value)
        android.util.Log.d("DuoViewModel", "updateFilteredSongs: ${filtered.size}")
        _filteredSongs.value = filtered
    }
    
    private fun filterAndSortSongs(songs: List<Song>, query: String, sort: DuoSortOption): List<Song> {
        var filtered = if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
        
        filtered = when (sort) {
            DuoSortOption.NAME -> filtered.sortedBy { it.title.lowercase() }
            DuoSortOption.TIME -> filtered.sortedByDescending { it.dateAdded }
            DuoSortOption.DURATION -> filtered.sortedByDescending { it.duration }
        }
        
        return filtered
    }

    private fun observeIncomingCommands() {
        viewModelScope.launch {
            incomingCommand.collect { command ->
                handleIncomingCommand(command)
            }
        }
    }

    private fun handleIncomingCommand(command: DuoCommand) {
        android.util.Log.d("DuoViewModel", "Handling incoming command: $command")
        
        isProcessingRemoteCommand = true
        
        when (command) {
            is DuoCommand.Play -> {
                // Try to find song from appropriate repository
                val song = if (webRTCRepository.isConnected()) {
                    webRTCRepository.findSongByHash(command.songHash)
                } else {
                    repository.findSongByHash(command.songHash)
                }
                android.util.Log.d("DuoViewModel", "Play command received, hash=${command.songHash}, found song=${song?.title}")
                song?.let {
                    _currentSong.value = it
                    _isPlaying.value = true
                    viewModelScope.launch {
                        val playlist = filteredSongs.value.ifEmpty { listOf(it) }
                        android.util.Log.d("DuoViewModel", "Emitting playSongEvent for ${it.title}")
                        _playSongEvent.emit(Pair(it, playlist))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } ?: run {
                    isProcessingRemoteCommand = false
                    viewModelScope.launch {
                        _toastMessage.emit("Song not found on this device")
                    }
                }
            }
            is DuoCommand.Pause -> {
                android.util.Log.d("DuoViewModel", "Pause command received")
                _isPlaying.value = false
                viewModelScope.launch {
                    _pauseEvent.emit(Unit)
                    kotlinx.coroutines.delay(500)
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.Resume -> {
                android.util.Log.d("DuoViewModel", "Resume command received")
                _isPlaying.value = true
                viewModelScope.launch {
                    _resumeEvent.emit(Unit)
                    kotlinx.coroutines.delay(500)
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.Seek -> {
                android.util.Log.d("DuoViewModel", "Seek command received: ${command.position}")
                viewModelScope.launch {
                    _seekEvent.emit(command.position)
                    kotlinx.coroutines.delay(500)
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.Next -> {
                android.util.Log.d("DuoViewModel", "Next command received")
                val songs = filteredSongs.value
                val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
                if (currentIndex >= 0 && currentIndex < songs.size - 1) {
                    val nextSong = songs[currentIndex + 1]
                    _currentSong.value = nextSong
                    _isPlaying.value = true
                    viewModelScope.launch {
                        _playSongEvent.emit(Pair(nextSong, songs))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } else if (_repeatMode.value == RepeatMode.ALL && songs.isNotEmpty()) {
                    val firstSong = songs.first()
                    _currentSong.value = firstSong
                    _isPlaying.value = true
                    viewModelScope.launch {
                        _playSongEvent.emit(Pair(firstSong, songs))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } else {
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.Previous -> {
                android.util.Log.d("DuoViewModel", "Previous command received")
                val songs = filteredSongs.value
                val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
                if (currentIndex > 0) {
                    val prevSong = songs[currentIndex - 1]
                    _currentSong.value = prevSong
                    _isPlaying.value = true
                    viewModelScope.launch {
                        _playSongEvent.emit(Pair(prevSong, songs))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } else if (_repeatMode.value == RepeatMode.ALL && songs.isNotEmpty()) {
                    val lastSong = songs.last()
                    _currentSong.value = lastSong
                    _isPlaying.value = true
                    viewModelScope.launch {
                        _playSongEvent.emit(Pair(lastSong, songs))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } else {
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.SetShuffle -> {
                android.util.Log.d("DuoViewModel", "SetShuffle command received: ${command.enabled}")
                _shuffleEnabled.value = command.enabled
                isProcessingRemoteCommand = false
            }
            is DuoCommand.SetRepeat -> {
                android.util.Log.d("DuoViewModel", "SetRepeat command received: ${command.mode}")
                _repeatMode.value = command.mode
                isProcessingRemoteCommand = false
            }
            is DuoCommand.AddToQueue -> {
                android.util.Log.d("DuoViewModel", "AddToQueue command received")
                isProcessingRemoteCommand = false
            }
            is DuoCommand.ClearQueue -> {
                android.util.Log.d("DuoViewModel", "ClearQueue command received")
                isProcessingRemoteCommand = false
            }
            is DuoCommand.RequestDisconnect -> {
                isProcessingRemoteCommand = false
                viewModelScope.launch {
                    _toastMessage.emit("Partner disconnected")
                }
            }
        }
    }

    fun setLocalSongs(songs: List<Song>) {
        android.util.Log.d("DuoViewModel", "setLocalSongs called with ${songs.size} songs")
        _allSongs.value = songs
        repository.setLocalSongs(songs)
        webRTCRepository.setLocalSongs(songs) // Also set for WebRTC
        
        viewModelScope.launch {
            _toastMessage.emit("Local songs loaded: ${songs.size}")
        }
    }
    
    fun setLocalVideos(videos: List<Video>) {
        android.util.Log.d("DuoViewModel", "setLocalVideos called with ${videos.size} videos")
        _localVideos.value = videos
        // Update common videos if we already have common songs
        if (_combinedCommonSongs.value.isNotEmpty()) {
            updateCommonVideos()
        }
    }
    
    // Note: setLocalArtists, setLocalAlbums, setLocalFolders are kept for compatibility
    // but Artists/Albums/Folders are now derived purely from common songs
    fun setLocalArtists(artists: List<Artist>) {
        android.util.Log.d("DuoViewModel", "setLocalArtists called with ${artists.size} artists (not used - derived from common songs)")
        _localArtists.value = artists
    }
    
    fun setLocalAlbums(albums: List<Album>) {
        android.util.Log.d("DuoViewModel", "setLocalAlbums called with ${albums.size} albums (not used - derived from common songs)")
        _localAlbums.value = albums
    }
    
    fun setLocalFolders(folders: List<Folder>) {
        android.util.Log.d("DuoViewModel", "setLocalFolders called with ${folders.size} folders (not used - derived from common songs)")
        _localFolders.value = folders
    }
    
    /**
     * Get songs for a specific artist from common songs
     */
    fun getSongsForArtist(artistName: String): List<Song> {
        return _combinedCommonSongs.value.filter { 
            it.artist.equals(artistName, ignoreCase = true) 
        }
    }
    
    /**
     * Get songs for a specific album from common songs
     */
    fun getSongsForAlbum(albumTitle: String, artistName: String): List<Song> {
        return _combinedCommonSongs.value.filter { 
            it.album.equals(albumTitle, ignoreCase = true) &&
            it.artist.equals(artistName, ignoreCase = true)
        }
    }
    
    /**
     * Get songs for a specific folder from common songs
     */
    fun getSongsForFolder(folderPath: String): List<Song> {
        return _combinedCommonSongs.value.filter { song ->
            song.path.substringBeforeLast("/") == folderPath
        }
    }
    
    /**
     * Update common videos based on common songs
     * Videos are considered common if they match by title and artist
     */
    private fun updateCommonVideos() {
        val commonSongs = _combinedCommonSongs.value
        val localVideos = _localVideos.value
        
        android.util.Log.d("DuoViewModel", "updateCommonVideos: commonSongs=${commonSongs.size}, localVideos=${localVideos.size}")
        
        if (commonSongs.isEmpty() || localVideos.isEmpty()) {
            _commonVideos.value = emptyList()
            _filteredVideos.value = emptyList()
            return
        }
        
        // Match videos by title (case-insensitive)
        val commonVideoTitles = commonSongs.map { it.title.lowercase() }.toSet()
        val common = localVideos.filter { video ->
            commonVideoTitles.contains(video.title.lowercase())
        }
        
        _commonVideos.value = common
        _filteredVideos.value = common
        android.util.Log.d("DuoViewModel", "Common videos updated: ${common.size}")
    }
    
    /**
     * Update common artists - derived purely from common songs
     * Creates Artist objects from the unique artists in common songs
     */
    private fun updateCommonArtists() {
        val commonSongs = _combinedCommonSongs.value
        
        android.util.Log.d("DuoViewModel", "updateCommonArtists: commonSongs=${commonSongs.size}")
        
        if (commonSongs.isEmpty()) {
            _commonArtists.value = emptyList()
            _filteredArtists.value = emptyList()
            return
        }
        
        // Group common songs by artist and create Artist objects
        val artistsMap = commonSongs.groupBy { it.artist.lowercase() }
        val common = artistsMap.map { (_, songs) ->
            val firstSong = songs.first()
            Artist(
                id = firstSong.artist.hashCode().toLong(),
                name = firstSong.artist,
                songCount = songs.size,
                albumCount = songs.map { it.album }.distinct().size
            )
        }.sortedBy { it.name.lowercase() }
        
        _commonArtists.value = common
        _filteredArtists.value = common
        android.util.Log.d("DuoViewModel", "Common artists updated: ${common.size} - ${common.map { it.name }}")
    }
    
    /**
     * Update common albums - derived purely from common songs
     * Creates Album objects from the unique albums in common songs
     */
    private fun updateCommonAlbums() {
        val commonSongs = _combinedCommonSongs.value
        
        android.util.Log.d("DuoViewModel", "updateCommonAlbums: commonSongs=${commonSongs.size}")
        
        if (commonSongs.isEmpty()) {
            _commonAlbums.value = emptyList()
            _filteredAlbums.value = emptyList()
            return
        }
        
        // Group common songs by album+artist and create Album objects
        val albumsMap = commonSongs.groupBy { "${it.album.lowercase()}|${it.artist.lowercase()}" }
        val common = albumsMap.map { (_, songs) ->
            val firstSong = songs.first()
            Album(
                id = "${firstSong.album}|${firstSong.artist}".hashCode().toLong(),
                title = firstSong.album,
                artist = firstSong.artist,
                songCount = songs.size,
                albumArtUri = firstSong.albumArtUri
            )
        }.sortedBy { it.title.lowercase() }
        
        _commonAlbums.value = common
        _filteredAlbums.value = common
        android.util.Log.d("DuoViewModel", "Common albums updated: ${common.size} - ${common.map { it.title }}")
    }
    
    /**
     * Update common folders - derived purely from common songs
     * Creates Folder objects from the unique folders containing common songs
     */
    private fun updateCommonFolders() {
        val commonSongs = _combinedCommonSongs.value
        
        android.util.Log.d("DuoViewModel", "updateCommonFolders: commonSongs=${commonSongs.size}")
        
        if (commonSongs.isEmpty()) {
            _commonFolders.value = emptyList()
            _filteredFolders.value = emptyList()
            return
        }
        
        // Group common songs by their parent folder path
        val foldersMap = commonSongs.groupBy { song ->
            song.path.substringBeforeLast("/")
        }
        
        val common = foldersMap.map { (folderPath, songs) ->
            val folderName = folderPath.substringAfterLast("/")
            Folder(
                id = folderPath.hashCode().toLong(),
                name = folderName.ifEmpty { "Root" },
                path = folderPath,
                songCount = songs.size,
                videoCount = 0
            )
        }.sortedBy { it.name.lowercase() }
        
        _commonFolders.value = common
        _filteredFolders.value = common
        android.util.Log.d("DuoViewModel", "Common folders updated: ${common.size} - ${common.map { it.name }}")
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: DuoSortOption) {
        _sortOption.value = option
    }

    fun hideConnectionSheet() {
        _showConnectionSheet.value = false
    }

    fun startDiscovery() {
        repository.startDiscovery(
            onSuccess = {
                viewModelScope.launch {
                    _toastMessage.emit("Searching for devices...")
                }
            },
            onFailure = { error ->
                viewModelScope.launch {
                    _toastMessage.emit(error)
                }
            }
        )
    }

    fun connectToDevice(device: DuoDevice) {
        repository.connectToDevice(
            device = device,
            onSuccess = {
                viewModelScope.launch {
                    _toastMessage.emit("Connecting to ${device.deviceName}...")
                    hideConnectionSheet()
                }
            },
            onFailure = { error ->
                viewModelScope.launch {
                    _toastMessage.emit(error)
                }
            }
        )
    }

    fun disconnect() {
        // Disconnect both WiFi Direct and WebRTC
        repository.disconnect()
        webRTCRepository.disconnect()
        _currentSong.value = null
        _isPlaying.value = false
        _combinedCommonSongs.value = emptyList()
        _filteredSongs.value = emptyList()
        _commonVideos.value = emptyList()
        _filteredVideos.value = emptyList()
        _commonArtists.value = emptyList()
        _filteredArtists.value = emptyList()
        _commonAlbums.value = emptyList()
        _filteredAlbums.value = emptyList()
        _commonFolders.value = emptyList()
        _filteredFolders.value = emptyList()
        _combinedSignalStrength.value = SignalStrength.NONE
        clearChatMessages()
        viewModelScope.launch {
            _toastMessage.emit("Disconnected")
        }
    }
    
    fun resyncLibrary() {
        // Resync via appropriate connection type
        if (webRTCRepository.isConnected()) {
            webRTCRepository.resyncLibrary()
        } else {
            repository.resyncLibrary()
        }
        viewModelScope.launch {
            _toastMessage.emit("Syncing library...")
        }
    }

    fun cancelConnection() {
        repository.cancelConnection()
        viewModelScope.launch {
            _toastMessage.emit("Connection cancelled")
        }
    }
    
    /**
     * Check if connected via any method (WiFi Direct or WebRTC)
     */
    fun isConnected(): Boolean {
        return connectionState.value is DuoConnectionState.Connected ||
                webRTCRepository.isConnected()
    }
    
    /**
     * Check if a song is in the common songs list (available to both partners)
     */
    fun isSongInCommonList(song: Song): Boolean {
        return _combinedCommonSongs.value.any { it.id == song.id }
    }

    // Playback controls

    fun playSong(song: Song) {
        _currentSong.value = song
        _isPlaying.value = true
        
        viewModelScope.launch {
            val playlist = filteredSongs.value.ifEmpty { listOf(song) }
            _playSongEvent.emit(Pair(song, playlist))
        }
        
        // Send command to partner device via appropriate channel
        viewModelScope.launch {
            val songHash = repository.getSongHash(song)
            android.util.Log.d("DuoViewModel", "Sending play command for ${song.title}, hash=$songHash")
            
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendPlay(songHash)
            } else {
                repository.sendPlay(songHash)
            }
        }
    }

    fun pause() {
        android.util.Log.d("DuoViewModel", "pause() called")
        _isPlaying.value = false
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendPause()
            } else {
                repository.sendPause()
            }
        }
    }

    fun resume() {
        android.util.Log.d("DuoViewModel", "resume() called")
        _isPlaying.value = true
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendResume()
            } else {
                repository.sendResume()
            }
        }
    }

    fun seekTo(position: Long) {
        android.util.Log.d("DuoViewModel", "seekTo($position) called")
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendSeek(position)
            } else {
                repository.sendSeek(position)
            }
        }
    }
    
    fun syncPause() {
        if (isProcessingRemoteCommand) {
            android.util.Log.d("DuoViewModel", "syncPause() skipped - processing remote command")
            return
        }
        android.util.Log.d("DuoViewModel", "syncPause() called - sending to partner only")
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendPause()
            } else {
                repository.sendPause()
            }
        }
    }
    
    fun syncResume() {
        if (isProcessingRemoteCommand) {
            android.util.Log.d("DuoViewModel", "syncResume() skipped - processing remote command")
            return
        }
        android.util.Log.d("DuoViewModel", "syncResume() called - sending to partner only")
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendResume()
            } else {
                repository.sendResume()
            }
        }
    }
    
    fun syncSeek(position: Long) {
        if (isProcessingRemoteCommand) {
            android.util.Log.d("DuoViewModel", "syncSeek() skipped - processing remote command")
            return
        }
        android.util.Log.d("DuoViewModel", "syncSeek($position) called - sending to partner only")
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendSeek(position)
            } else {
                repository.sendSeek(position)
            }
        }
    }
    
    fun syncSongChange(song: Song) {
        if (isProcessingRemoteCommand) {
            android.util.Log.d("DuoViewModel", "syncSongChange() skipped - processing remote command")
            return
        }
        android.util.Log.d("DuoViewModel", "syncSongChange(${song.title}) called - sending to partner only")
        _currentSong.value = song
        _isPlaying.value = true
        viewModelScope.launch {
            val songHash = repository.getSongHash(song)
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendPlay(songHash)
            } else {
                repository.sendPlay(songHash)
            }
        }
    }

    fun playNext() {
        val songs = filteredSongs.value
        val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
        val nextSong = if (currentIndex >= 0 && currentIndex < songs.size - 1) {
            songs[currentIndex + 1]
        } else if (_repeatMode.value == RepeatMode.ALL && songs.isNotEmpty()) {
            songs.first()
        } else {
            null
        }
        
        nextSong?.let { song ->
            _currentSong.value = song
            _isPlaying.value = true
            viewModelScope.launch {
                _playSongEvent.emit(Pair(song, songs))
            }
            
            viewModelScope.launch {
                val songHash = repository.getSongHash(song)
                android.util.Log.d("DuoViewModel", "Sending play (next) command for ${song.title}")
                if (webRTCRepository.isConnected()) {
                    webRTCRepository.sendPlay(songHash)
                } else {
                    repository.sendPlay(songHash)
                }
            }
        }
    }

    fun playPrevious() {
        val songs = filteredSongs.value
        val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
        val prevSong = if (currentIndex > 0) {
            songs[currentIndex - 1]
        } else if (_repeatMode.value == RepeatMode.ALL && songs.isNotEmpty()) {
            songs.last()
        } else {
            null
        }
        
        prevSong?.let { song ->
            _currentSong.value = song
            _isPlaying.value = true
            viewModelScope.launch {
                _playSongEvent.emit(Pair(song, songs))
            }
            
            viewModelScope.launch {
                val songHash = repository.getSongHash(song)
                android.util.Log.d("DuoViewModel", "Sending play (previous) command for ${song.title}")
                if (webRTCRepository.isConnected()) {
                    webRTCRepository.sendPlay(songHash)
                } else {
                    repository.sendPlay(songHash)
                }
            }
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendRepeat(_repeatMode.value)
            } else {
                repository.sendRepeat(_repeatMode.value)
            }
        }
    }

    fun shufflePlay() {
        val songs = filteredSongs.value.shuffled()
        if (songs.isNotEmpty()) {
            _shuffleEnabled.value = true
            playSong(songs.first())
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupRecording()
        repository.cleanup()
        webRTCRepository.cleanup()
    }
}
