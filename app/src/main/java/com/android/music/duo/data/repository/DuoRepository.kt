package com.android.music.duo.data.repository

import android.content.Context
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import com.android.music.data.model.Song
import com.android.music.duo.data.model.*
import com.android.music.duo.service.ConnectionStatus
import com.android.music.duo.service.DuoSocketManager
import com.android.music.duo.service.WifiDirectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Repository for managing Duo connections and data synchronization
 */
class DuoRepository(context: Context) {

    companion object {
        private const val TAG = "DuoRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val wifiDirectManager = WifiDirectManager(context)
    private val socketManager = DuoSocketManager()

    private val _connectionState = MutableStateFlow<DuoConnectionState>(DuoConnectionState.Disconnected)
    val connectionState: StateFlow<DuoConnectionState> = _connectionState.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()
    
    // Track if this device initiated the connection (sent the invite)
    private var isConnectionInitiator = false

    private val _commonSongs = MutableStateFlow<List<Song>>(emptyList())
    val commonSongs: StateFlow<List<Song>> = _commonSongs.asStateFlow()

    private val _signalStrength = MutableStateFlow(SignalStrength.NONE)
    val signalStrength: StateFlow<SignalStrength> = _signalStrength.asStateFlow()

    private val _incomingCommand = MutableSharedFlow<DuoCommand>()
    val incomingCommand: SharedFlow<DuoCommand> = _incomingCommand.asSharedFlow()
    
    // Chat message handling
    private val _incomingChatMessage = MutableSharedFlow<ChatMessagePayload>()
    val incomingChatMessage: SharedFlow<ChatMessagePayload> = _incomingChatMessage.asSharedFlow()
    
    private val _incomingVoiceMessage = MutableSharedFlow<VoiceMessagePayload>()
    val incomingVoiceMessage: SharedFlow<VoiceMessagePayload> = _incomingVoiceMessage.asSharedFlow()
    
    private val _messageDelivered = MutableSharedFlow<String>()
    val messageDelivered: SharedFlow<String> = _messageDelivered.asSharedFlow()
    
    private val _messageRead = MutableSharedFlow<String>()
    val messageRead: SharedFlow<String> = _messageRead.asSharedFlow()
    
    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping: StateFlow<Boolean> = _isPartnerTyping.asStateFlow()

    val discoveredDevices: StateFlow<List<DuoDevice>> = wifiDirectManager.discoveredDevices
    val isWifiP2pEnabled: StateFlow<Boolean> = wifiDirectManager.isWifiP2pEnabled

    private var localSongs: List<Song> = emptyList()
    private var localSongHashes: Map<String, Song> = emptyMap()
    
    // Expose method to get hash for a song
    fun getSongHash(song: Song): String {
        return generateSongHash(song)
    }
    
    // Find song by hash
    fun findSongByHash(hash: String): Song? {
        return localSongHashes[hash]
    }

    init {
        wifiDirectManager.initialize()
        observeConnectionInfo()
        observeSocketMessages()
        observeSocketStatus()
        observeConnectionQuality()
    }
    
    /**
     * Observe socket connection quality and convert to SignalStrength
     */
    private fun observeConnectionQuality() {
        scope.launch {
            socketManager.connectionQuality.collect { quality ->
                _signalStrength.value = qualityToSignalStrength(quality)
            }
        }
    }
    
    /**
     * Convert quality score (0-100) to SignalStrength enum
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

    private fun observeConnectionInfo() {
        scope.launch {
            wifiDirectManager.connectionInfo.collect { info ->
                info?.let { handleConnectionInfo(it) }
            }
        }
    }

    private fun observeSocketMessages() {
        scope.launch {
            socketManager.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    private fun observeSocketStatus() {
        scope.launch {
            socketManager.connectionStatus.collect { status ->
                Log.d(TAG, "Socket status changed: $status")
                when (status) {
                    is ConnectionStatus.Connected -> {
                        // Signal strength will be updated by observeConnectionQuality
                        // Set initial value to GOOD until ping measurements come in
                        _signalStrength.value = SignalStrength.GOOD
                        
                        // Update connection state to Connected immediately
                        val currentState = _connectionState.value
                        if (currentState is DuoConnectionState.Connecting) {
                            _connectionState.value = DuoConnectionState.Connected(currentState.device, _isHost.value)
                        } else {
                            // Create a dummy device if we don't have one
                            _connectionState.value = DuoConnectionState.Connected(
                                DuoDevice("Partner Device", "", false),
                                _isHost.value
                            )
                        }
                        
                        // Both host and guest send their library for sync
                        // Add a small delay to ensure socket is fully ready
                        Log.d(TAG, "Socket connected, waiting before sending library sync. isHost=${_isHost.value}, localSongs=${localSongs.size}")
                        kotlinx.coroutines.delay(1000) // Wait 1 second for socket to stabilize
                        
                        // Send library sync
                        sendLibrarySync()
                        
                        // Wait and send again to ensure both sides have synced
                        kotlinx.coroutines.delay(2000)
                        Log.d(TAG, "Sending second library sync to ensure both sides are synced")
                        sendLibrarySync()
                    }
                    is ConnectionStatus.Disconnected -> {
                        _connectionState.value = DuoConnectionState.Disconnected
                        _signalStrength.value = SignalStrength.NONE
                        _commonSongs.value = emptyList()
                    }
                    is ConnectionStatus.Error -> {
                        _connectionState.value = DuoConnectionState.Error(status.message)
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun handleConnectionInfo(info: WifiP2pInfo) {
        Log.d(TAG, "WiFi P2P Connection info: groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}, isConnectionInitiator=$isConnectionInitiator")
        
        if (info.groupFormed) {
            // The one who initiated the connection (clicked connect) is the host
            _isHost.value = isConnectionInitiator
            Log.d(TAG, "Group formed. isHost=${_isHost.value} (initiator=$isConnectionInitiator, groupOwner=${info.isGroupOwner})")
            
            if (info.isGroupOwner) {
                // Start server as group owner (regardless of who is host)
                Log.d(TAG, "Starting server as group owner...")
                scope.launch {
                    socketManager.startServer()
                }
            } else {
                // Connect to group owner as client
                info.groupOwnerAddress?.hostAddress?.let { address ->
                    Log.d(TAG, "Connecting to group owner at $address...")
                    scope.launch {
                        socketManager.connectToServer(address)
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(message: DuoMessage) {
        Log.d(TAG, "Received message: ${message.type}")
        
        when (message.type) {
            MessageType.PLAY -> {
                val payload = parsePayload<PlayPayload>(message.payload)
                payload?.let {
                    _incomingCommand.emit(DuoCommand.Play(it.songHash, it.position))
                }
            }
            MessageType.PAUSE -> _incomingCommand.emit(DuoCommand.Pause)
            MessageType.RESUME -> _incomingCommand.emit(DuoCommand.Resume)
            MessageType.SEEK -> {
                val payload = parsePayload<SeekPayload>(message.payload)
                payload?.let {
                    _incomingCommand.emit(DuoCommand.Seek(it.position))
                }
            }
            MessageType.NEXT -> _incomingCommand.emit(DuoCommand.Next)
            MessageType.PREVIOUS -> _incomingCommand.emit(DuoCommand.Previous)
            MessageType.SHUFFLE -> {
                val payload = parsePayload<ShufflePayload>(message.payload)
                payload?.let {
                    _incomingCommand.emit(DuoCommand.SetShuffle(it.enabled))
                }
            }
            MessageType.REPEAT -> {
                val payload = parsePayload<RepeatPayload>(message.payload)
                payload?.let {
                    _incomingCommand.emit(DuoCommand.SetRepeat(RepeatMode.valueOf(it.mode)))
                }
            }
            MessageType.ADD_TO_QUEUE -> {
                val payload = parsePayload<QueuePayload>(message.payload)
                payload?.let {
                    _incomingCommand.emit(DuoCommand.AddToQueue(it.songHashes))
                }
            }
            MessageType.CLEAR_QUEUE -> _incomingCommand.emit(DuoCommand.ClearQueue)
            MessageType.SYNC_LIBRARY -> handleLibrarySync(message.payload)
            MessageType.SYNC_RESPONSE -> handleSyncResponse(message.payload)
            MessageType.DISCONNECT -> {
                _incomingCommand.emit(DuoCommand.RequestDisconnect)
                disconnect()
            }
            // Chat messages
            MessageType.CHAT_MESSAGE -> {
                val payload = parsePayload<ChatMessagePayload>(message.payload)
                payload?.let {
                    _incomingChatMessage.emit(it)
                    _isPartnerTyping.value = false
                }
            }
            MessageType.TYPING_START -> {
                _isPartnerTyping.value = true
            }
            MessageType.TYPING_STOP -> {
                _isPartnerTyping.value = false
            }
            MessageType.MESSAGE_DELIVERED -> {
                val payload = parsePayload<MessageAckPayload>(message.payload)
                payload?.let {
                    android.util.Log.d(TAG, "Received MESSAGE_DELIVERED for: ${it.messageId}")
                    _messageDelivered.emit(it.messageId)
                }
            }
            MessageType.MESSAGE_READ -> {
                val payload = parsePayload<MessageAckPayload>(message.payload)
                payload?.let {
                    android.util.Log.d(TAG, "Received MESSAGE_READ for: ${it.messageId}")
                    _messageRead.emit(it.messageId)
                }
            }
            MessageType.VOICE_MESSAGE -> {
                val payload = parsePayload<VoiceMessagePayload>(message.payload)
                payload?.let {
                    android.util.Log.d(TAG, "Received VOICE_MESSAGE from: ${it.senderName}")
                    _incomingVoiceMessage.emit(it)
                    _isPartnerTyping.value = false
                }
            }
            else -> {}
        }
    }

    private suspend fun handleLibrarySync(payload: String) {
        val syncPayload = parsePayload<SyncLibraryPayload>(payload) ?: run {
            Log.e(TAG, "Failed to parse library sync payload")
            return
        }
        Log.d(TAG, "Handling library sync with ${syncPayload.songHashes.size} songs from partner")
        Log.d(TAG, "Local songs: ${localSongs.size}, Local hashes: ${localSongHashes.size}")
        
        if (localSongHashes.isEmpty()) {
            Log.w(TAG, "Local song hashes are empty! Waiting for songs to load...")
            kotlinx.coroutines.delay(2000)
            Log.d(TAG, "After wait - Local songs: ${localSongs.size}, Local hashes: ${localSongHashes.size}")
            if (localSongHashes.isEmpty()) {
                Log.e(TAG, "Still no local songs. Sending empty response.")
                socketManager.sendMessage(DuoMessage.createSyncResponse(emptyList()))
                return
            }
        }
        
        // Find common songs by matching hashes
        val partnerHashes = syncPayload.songHashes.map { it.hash }.toSet()
        val commonHashes = localSongHashes.keys.filter { partnerHashes.contains(it) }
        
        Log.d(TAG, "Partner hashes: ${partnerHashes.size}, Common hashes: ${commonHashes.size}")
        
        // Update common songs list
        val common = commonHashes.mapNotNull { localSongHashes[it] }
        Log.d(TAG, "Mapped common songs: ${common.size}")
        
        // Update StateFlow - this is thread-safe
        _commonSongs.value = common
        Log.d(TAG, "Updated _commonSongs to ${_commonSongs.value.size}")
        
        // Send response with common hashes
        val responseSuccess = socketManager.sendMessage(DuoMessage.createSyncResponse(commonHashes))
        Log.d(TAG, "Sync response sent: $responseSuccess")
    }

    private suspend fun handleSyncResponse(payload: String) {
        val response = parsePayload<SyncResponsePayload>(payload) ?: run {
            Log.e(TAG, "Failed to parse sync response payload")
            return
        }
        Log.d(TAG, "Received sync response with ${response.commonHashes.size} common hashes")
        
        if (localSongHashes.isEmpty()) {
            Log.e(TAG, "Local song hashes are empty when handling sync response!")
            return
        }
        
        // Update common songs based on response
        val common = response.commonHashes.mapNotNull { hash ->
            val song = localSongHashes[hash]
            if (song == null) {
                Log.w(TAG, "Hash not found in local songs: $hash")
            }
            song
        }
        
        Log.d(TAG, "Mapped ${common.size} common songs from response")
        
        // Update StateFlow - this is thread-safe
        _commonSongs.value = common
        Log.d(TAG, "Updated _commonSongs to ${_commonSongs.value.size}")
    }

    private inline fun <reified T> parsePayload(payload: String): T? {
        return try {
            com.google.gson.Gson().fromJson(payload, T::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing payload", e)
            null
        }
    }

    fun setLocalSongs(songs: List<Song>) {
        Log.d(TAG, "Setting local songs: ${songs.size}")
        localSongs = songs
        localSongHashes = songs.associateBy { generateSongHash(it) }
        Log.d(TAG, "Generated ${localSongHashes.size} song hashes")
    }

    private fun generateSongHash(song: Song): String {
        val input = "${song.title}|${song.artist}|${song.duration}"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun sendLibrarySync() {
        Log.d(TAG, "Sending library sync with ${localSongs.size} songs")
        if (localSongs.isEmpty()) {
            Log.w(TAG, "No local songs to sync! Waiting for songs to be loaded...")
            // Wait a bit and retry - songs might not be loaded yet
            kotlinx.coroutines.delay(1000)
            if (localSongs.isEmpty()) {
                Log.e(TAG, "Still no local songs after waiting. Cannot sync library.")
                return
            }
        }
        
        val hashes = localSongs.map { song ->
            SongHash(
                id = song.id,
                hash = generateSongHash(song),
                title = song.title,
                artist = song.artist,
                duration = song.duration
            )
        }
        Log.d(TAG, "Created ${hashes.size} song hashes for sync")
        
        val success = socketManager.sendMessage(DuoMessage.createSyncLibrary(hashes))
        Log.d(TAG, "Library sync message sent: $success")
        
        if (!success) {
            Log.e(TAG, "Failed to send library sync, retrying in 1 second...")
            kotlinx.coroutines.delay(1000)
            val retrySuccess = socketManager.sendMessage(DuoMessage.createSyncLibrary(hashes))
            Log.d(TAG, "Library sync retry result: $retrySuccess")
        }
    }

    // Public API methods
    
    /**
     * Manually trigger a library sync. Useful if the initial sync failed.
     */
    fun resyncLibrary() {
        scope.launch {
            Log.d(TAG, "Manual library resync requested")
            sendLibrarySync()
        }
    }

    fun startDiscovery(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        _connectionState.value = DuoConnectionState.Searching
        wifiDirectManager.discoverPeers(
            onSuccess = onSuccess,
            onFailure = { reason ->
                val errorMsg = when (reason) {
                    0 -> "Discovery failed. Please ensure WiFi and Location are enabled."
                    1 -> "WiFi Direct is not supported on this device"
                    2 -> "System is busy, please try again"
                    -1 -> "WiFi P2P not initialized"
                    else -> "Discovery failed (error: $reason)"
                }
                _connectionState.value = DuoConnectionState.Error(errorMsg)
                onFailure(errorMsg)
            }
        )
    }

    fun stopDiscovery() {
        wifiDirectManager.stopDiscovery()
        if (_connectionState.value is DuoConnectionState.Searching) {
            _connectionState.value = DuoConnectionState.Disconnected
        }
    }

    fun connectToDevice(device: DuoDevice, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        // Mark this device as the connection initiator (host)
        isConnectionInitiator = true
        _connectionState.value = DuoConnectionState.Connecting(device)
        wifiDirectManager.connectToDevice(
            device = device,
            onSuccess = onSuccess,
            onFailure = { reason ->
                isConnectionInitiator = false
                _connectionState.value = DuoConnectionState.Error("Connection failed: $reason")
                onFailure("Connection failed with reason: $reason")
            }
        )
    }

    fun cancelConnection() {
        isConnectionInitiator = false
        wifiDirectManager.cancelConnect(
            onSuccess = {
                _connectionState.value = DuoConnectionState.Disconnected
                Log.d(TAG, "Connection cancelled")
            },
            onFailure = { reason ->
                Log.e(TAG, "Failed to cancel connection: $reason")
                // Still reset state
                _connectionState.value = DuoConnectionState.Disconnected
            }
        )
    }

    fun disconnect() {
        scope.launch {
            socketManager.sendMessage(DuoMessage.createDisconnect())
        }
        socketManager.disconnect()
        wifiDirectManager.disconnect()
        _connectionState.value = DuoConnectionState.Disconnected
        _commonSongs.value = emptyList()
        _signalStrength.value = SignalStrength.NONE
        isConnectionInitiator = false
    }

    // Command sending methods (both devices can send)

    suspend fun sendPlay(songHash: String, position: Long = 0) {
        Log.d(TAG, "sendPlay called, songHash=$songHash")
        val success = socketManager.sendMessage(DuoMessage.createPlay(songHash, position))
        Log.d(TAG, "Play message sent: $success")
    }

    suspend fun sendPause() {
        Log.d(TAG, "sendPause called")
        val success = socketManager.sendMessage(DuoMessage.createPause())
        Log.d(TAG, "Pause message sent: $success")
    }

    suspend fun sendResume() {
        Log.d(TAG, "sendResume called")
        val success = socketManager.sendMessage(DuoMessage.createResume())
        Log.d(TAG, "Resume message sent: $success")
    }

    suspend fun sendSeek(position: Long) {
        Log.d(TAG, "sendSeek called, position=$position")
        val success = socketManager.sendMessage(DuoMessage.createSeek(position))
        Log.d(TAG, "Seek message sent: $success")
    }

    suspend fun sendNext() {
        Log.d(TAG, "sendNext called")
        val success = socketManager.sendMessage(DuoMessage.createNext())
        Log.d(TAG, "Next message sent: $success")
    }

    suspend fun sendPrevious() {
        Log.d(TAG, "sendPrevious called")
        val success = socketManager.sendMessage(DuoMessage.createPrevious())
        Log.d(TAG, "Previous message sent: $success")
    }

    suspend fun sendShuffle(enabled: Boolean) {
        Log.d(TAG, "sendShuffle called, enabled=$enabled")
        val success = socketManager.sendMessage(DuoMessage.createShuffle(enabled))
        Log.d(TAG, "Shuffle message sent: $success")
    }

    suspend fun sendRepeat(mode: RepeatMode) {
        Log.d(TAG, "sendRepeat called, mode=$mode")
        val success = socketManager.sendMessage(DuoMessage.createRepeat(mode))
        Log.d(TAG, "Repeat message sent: $success")
    }

    suspend fun sendAddToQueue(songHashes: List<String>) {
        Log.d(TAG, "sendAddToQueue called")
        val success = socketManager.sendMessage(DuoMessage.createAddToQueue(songHashes))
        Log.d(TAG, "AddToQueue message sent: $success")
    }

    suspend fun sendClearQueue() {
        Log.d(TAG, "sendClearQueue called")
        val success = socketManager.sendMessage(DuoMessage.createClearQueue())
        Log.d(TAG, "ClearQueue message sent: $success")
    }
    
    // Chat methods
    
    suspend fun sendChatMessage(message: DuoMessage): Boolean {
        Log.d(TAG, "sendChatMessage called")
        return socketManager.sendMessage(message)
    }
    
    suspend fun sendMessage(message: DuoMessage): Boolean {
        return socketManager.sendMessage(message)
    }
    
    suspend fun sendTypingStart() {
        socketManager.sendMessage(DuoMessage.createTypingStart())
    }
    
    suspend fun sendTypingStop() {
        socketManager.sendMessage(DuoMessage.createTypingStop())
    }

    fun cleanup() {
        disconnect()
        wifiDirectManager.cleanup()
    }
}
