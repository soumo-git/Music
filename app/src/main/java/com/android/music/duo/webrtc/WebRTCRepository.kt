package com.android.music.duo.webrtc

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.music.data.model.Song
import com.android.music.duo.data.model.ChatMessagePayload
import com.android.music.duo.data.model.DuoCommand
import com.android.music.duo.data.model.DuoMessage
import com.android.music.duo.data.model.MessageAckPayload
import com.android.music.duo.data.model.MessageType
import com.android.music.duo.data.model.PlayPayload
import com.android.music.duo.data.model.RepeatMode
import com.android.music.duo.data.model.RepeatPayload
import com.android.music.duo.data.model.SeekPayload
import com.android.music.duo.data.model.ShufflePayload
import com.android.music.duo.data.model.SongHash
import com.android.music.duo.data.model.SyncLibraryPayload
import com.android.music.duo.data.model.SyncResponsePayload
import com.android.music.duo.data.model.VoiceMessagePayload
import com.android.music.duo.webrtc.model.PresenceStatus
import com.android.music.duo.webrtc.model.WebRTCConnectionState
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Repository coordinating all WebRTC components for Duo long-distance connections
 */
class WebRTCRepository(private val context: Context) {

    companion object {
        private const val TAG = "WebRTCRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val duoIdManager = DuoIdManager.getInstance(context)
    private val presenceManager = PresenceManager.getInstance()
    private val signalingManager = SignalingManager.getInstance()
    private val webRTCManager = WebRTCManager(context)

    // My Duo ID
    private var myDuoId: String? = null
    
    // Current partner info
    private var partnerId: String? = null
    private var partnerDeviceName: String? = null

    // Local songs for library sync
    private var localSongs: List<Song> = emptyList()
    private var localSongHashes: Map<String, Song> = emptyMap()

    // Connection state
    private val _connectionState = MutableStateFlow<WebRTCConnectionState>(WebRTCConnectionState.Idle)
    val connectionState: StateFlow<WebRTCConnectionState> = _connectionState.asStateFlow()

    // Partner presence
    private val _partnerPresence = MutableStateFlow<PresenceStatus?>(null)
    val partnerPresence: StateFlow<PresenceStatus?> = _partnerPresence.asStateFlow()

    // Incoming connection request (offer)
    private val _incomingRequest = MutableStateFlow<SignalingManager.IncomingOffer?>(null)
    val incomingRequest: StateFlow<SignalingManager.IncomingOffer?> = _incomingRequest.asStateFlow()

    // Common songs (synced between devices)
    private val _commonSongs = MutableStateFlow<List<Song>>(emptyList())
    val commonSongs: StateFlow<List<Song>> = _commonSongs.asStateFlow()

    // Incoming commands from partner
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
    
    // Connection quality (0-100)
    val connectionQuality: StateFlow<Int> = webRTCManager.connectionQuality

    // Events
    private val _events = MutableSharedFlow<WebRTCEvent>()
    val events: SharedFlow<WebRTCEvent> = _events.asSharedFlow()

    // Jobs for observation
    private var incomingOfferJob: Job? = null
    private var answerJob: Job? = null

    sealed class WebRTCEvent {
        data class Error(val message: String) : WebRTCEvent()
        data class PartnerOffline(val lastSeen: String, val deviceName: String) : WebRTCEvent()
        object Connected : WebRTCEvent()
        object Disconnected : WebRTCEvent()
        data class IncomingRequest(val fromId: String, val fromDeviceName: String) : WebRTCEvent()
    }

    /**
     * Initialize WebRTC repository
     */
    suspend fun initialize(): String {
        // Get or create Duo ID
        myDuoId = duoIdManager.getOrCreateDuoId()
        Log.d(TAG, "My Duo ID: $myDuoId")

        // Start presence tracking
        myDuoId?.let { id ->
            presenceManager.startPresenceTracking(id)
            observeIncomingOffers(id)
        }

        // Observe WebRTC messages
        observeWebRTCMessages()

        return myDuoId ?: ""
    }

    /**
     * Get my Duo ID
     */
    fun getMyDuoId(): String? = myDuoId

    /**
     * Validate if a Duo ID exists
     */
    suspend fun validateDuoId(duoId: String): Boolean {
        return duoIdManager.validateDuoId(duoId)
    }

    /**
     * Check partner's presence status
     */
    suspend fun checkPartnerPresence(partnerId: String): PresenceStatus? {
        val presence = presenceManager.getPresenceStatus(partnerId)
        _partnerPresence.value = presence
        return presence
    }

    /**
     * Connect to partner by Duo ID
     */
    suspend fun connectToPartner(targetDuoId: String) {
        if (myDuoId == null) {
            _connectionState.value = WebRTCConnectionState.Error("Not initialized")
            _events.emit(WebRTCEvent.Error("Not initialized"))
            return
        }

        if (targetDuoId == myDuoId) {
            _connectionState.value = WebRTCConnectionState.Error("Cannot connect to yourself")
            _events.emit(WebRTCEvent.Error("Cannot connect to yourself"))
            return
        }

        Log.d(TAG, "Connecting to partner: $targetDuoId")
        _connectionState.value = WebRTCConnectionState.CheckingPartner

        // Validate target ID
        val isValid = validateDuoId(targetDuoId)
        Log.d(TAG, "Target ID valid: $isValid")
        
        if (!isValid) {
            _connectionState.value = WebRTCConnectionState.Error("Invalid Duo ID")
            _events.emit(WebRTCEvent.Error("Invalid Duo ID"))
            return
        }

        // Check if partner is online
        val presence = checkPartnerPresence(targetDuoId)
        Log.d(TAG, "Partner presence: $presence")
        
        if (presence == null) {
            _connectionState.value = WebRTCConnectionState.Error("User not found")
            _events.emit(WebRTCEvent.Error("User not found"))
            return
        }

        if (!presence.online) {
            val lastSeenStr = presenceManager.formatLastSeen(presence.lastSeen)
            Log.d(TAG, "Partner is offline, last seen: $lastSeenStr")
            _connectionState.value = WebRTCConnectionState.PartnerOffline(presence.lastSeen, presence.deviceName)
            _events.emit(WebRTCEvent.PartnerOffline(lastSeenStr, presence.deviceName))
            return
        }

        // Partner is online, create offer
        partnerId = targetDuoId
        partnerDeviceName = presence.deviceName
        
        Log.d(TAG, "Partner is online, creating offer...")
        _connectionState.value = WebRTCConnectionState.CreatingOffer

        val offer = webRTCManager.createOffer()
        if (offer == null) {
            Log.e(TAG, "Failed to create offer")
            _connectionState.value = WebRTCConnectionState.Error("Failed to create offer")
            _events.emit(WebRTCEvent.Error("Failed to create offer"))
            return
        }

        Log.d(TAG, "Offer created, length: ${offer.length}, sending to Firebase...")
        
        // Send offer through Firebase (to partner's presence node)
        _connectionState.value = WebRTCConnectionState.WaitingForAnswer
        
        val sent = signalingManager.sendOffer(
            fromId = myDuoId!!,
            toId = targetDuoId,
            sdpOffer = offer,
            fromDeviceName = Build.MODEL
        )

        if (!sent) {
            Log.e(TAG, "Failed to send offer to Firebase")
            _connectionState.value = WebRTCConnectionState.Error("Failed to send offer")
            _events.emit(WebRTCEvent.Error("Failed to send offer"))
            return
        }

        Log.d(TAG, "Offer sent, waiting for answer...")
        
        // Observe for answer on my presence node
        observeAnswer()
    }

    /**
     * Request notification when partner comes online
     */
    suspend fun requestNotifyWhenOnline(targetDuoId: String): Boolean {
        val myId = myDuoId ?: return false
        return signalingManager.requestNotifyWhenOnline(
            requesterId = myId,
            requesterDeviceName = Build.MODEL,
            targetId = targetDuoId
        )
    }

    /**
     * Accept incoming connection request (offer)
     */
    suspend fun acceptIncomingRequest(offer: SignalingManager.IncomingOffer) {
        val myId = myDuoId ?: return
        
        partnerId = offer.fromId
        partnerDeviceName = offer.fromDeviceName
        
        Log.d(TAG, "Accepting offer from ${offer.fromId}, creating answer...")
        _connectionState.value = WebRTCConnectionState.Connecting

        // Create answer
        val answer = webRTCManager.createAnswer(offer.sdpOffer)
        if (answer == null) {
            Log.e(TAG, "Failed to create answer")
            _connectionState.value = WebRTCConnectionState.Error("Failed to create answer")
            _events.emit(WebRTCEvent.Error("Failed to create answer"))
            return
        }

        Log.d(TAG, "Answer created, length: ${answer.length}, sending to Firebase...")

        // Send answer to caller's presence node
        val sent = signalingManager.sendAnswer(
            fromId = myId,
            toId = offer.fromId,
            sdpAnswer = answer
        )

        if (!sent) {
            Log.e(TAG, "Failed to send answer")
            _connectionState.value = WebRTCConnectionState.Error("Failed to send answer")
            _events.emit(WebRTCEvent.Error("Failed to send answer"))
            return
        }

        Log.d(TAG, "Answer sent, connection should establish...")
        
        // Clear the incoming request
        _incomingRequest.value = null
        
        // Clear signaling data from my node
        signalingManager.clearSignalingData(myId)
    }

    /**
     * Reject incoming connection request
     */
    fun rejectIncomingRequest(offer: SignalingManager.IncomingOffer) {
        val myId = myDuoId ?: return
        // Clear the offer from my presence node
        signalingManager.clearSignalingData(myId)
        _incomingRequest.value = null
    }

    /**
     * Observe incoming offers on my presence node
     */
    private fun observeIncomingOffers(myId: String) {
        incomingOfferJob?.cancel()
        incomingOfferJob = scope.launch {
            signalingManager.observeIncomingOffer(myId).collect { offer ->
                if (offer != null) {
                    Log.d(TAG, "Incoming offer from ${offer.fromId} (${offer.fromDeviceName})")
                    _incomingRequest.value = offer
                    _events.emit(WebRTCEvent.IncomingRequest(offer.fromId, offer.fromDeviceName))
                }
            }
        }
    }

    /**
     * Observe answer on my presence node (after sending offer)
     */
    private fun observeAnswer() {
        val myId = myDuoId ?: return
        
        answerJob?.cancel()
        answerJob = scope.launch {
            signalingManager.observeAnswer(myId).collect { answer ->
                if (answer != null) {
                    Log.d(TAG, "Received answer, length: ${answer.length}")
                    
                    // Set remote answer
                    val success = webRTCManager.setRemoteAnswer(answer)
                    if (success) {
                        Log.d(TAG, "Remote answer set, connection should establish...")
                        // Clear signaling data
                        signalingManager.clearSignalingData(myId)
                    } else {
                        Log.e(TAG, "Failed to set remote answer")
                        _connectionState.value = WebRTCConnectionState.Error("Failed to establish connection")
                    }
                    
                    // Stop observing
                    answerJob?.cancel()
                }
            }
        }
    }

    /**
     * Observe WebRTC data channel messages
     */
    private fun observeWebRTCMessages() {
        scope.launch {
            webRTCManager.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }

        scope.launch {
            webRTCManager.connectionState.collect { state ->
                when (state) {
                    is WebRTCManager.WebRTCConnectionState.Connected -> {
                        _connectionState.value = WebRTCConnectionState.Connected(
                            partnerId = partnerId ?: "",
                            partnerDeviceName = partnerDeviceName ?: ""
                        )
                        _events.emit(WebRTCEvent.Connected)
                    }
                    is WebRTCManager.WebRTCConnectionState.Disconnected -> {
                        _connectionState.value = WebRTCConnectionState.Disconnected
                        _events.emit(WebRTCEvent.Disconnected)
                        _commonSongs.value = emptyList()
                    }
                    is WebRTCManager.WebRTCConnectionState.Error -> {
                        _connectionState.value = WebRTCConnectionState.Error(state.message)
                        _events.emit(WebRTCEvent.Error(state.message))
                    }
                    else -> {}
                }
            }
        }

        // Observe data channel state and trigger library sync when open
        scope.launch {
            webRTCManager.dataChannelOpen.collect { open ->
                if (open) {
                    Log.d(TAG, "Data channel is now open, initiating library sync...")
                    // Wait a bit for the channel to stabilize
                    delay(1000)
                    sendLibrarySync()
                    // Send again after a delay to ensure both sides sync
                    delay(2000)
                    Log.d(TAG, "Sending second library sync")
                    sendLibrarySync()
                }
            }
        }
    }

    /**
     * Handle incoming message from data channel
     */
    private suspend fun handleIncomingMessage(messageJson: String) {
        try {
            val message = gson.fromJson(messageJson, DuoMessage::class.java)
            Log.d(TAG, "Received message type: ${message.type}")

            when (message.type) {
                MessageType.PLAY -> {
                    val payload = gson.fromJson(message.payload, PlayPayload::class.java)
                    _incomingCommand.emit(DuoCommand.Play(payload.songHash, payload.position))
                }
                MessageType.PAUSE -> _incomingCommand.emit(DuoCommand.Pause)
                MessageType.RESUME -> _incomingCommand.emit(DuoCommand.Resume)
                MessageType.SEEK -> {
                    val payload = gson.fromJson(message.payload, SeekPayload::class.java)
                    _incomingCommand.emit(DuoCommand.Seek(payload.position))
                }
                MessageType.NEXT -> _incomingCommand.emit(DuoCommand.Next)
                MessageType.PREVIOUS -> _incomingCommand.emit(DuoCommand.Previous)
                MessageType.SHUFFLE -> {
                    val payload = gson.fromJson(message.payload, ShufflePayload::class.java)
                    _incomingCommand.emit(DuoCommand.SetShuffle(payload.enabled))
                }
                MessageType.REPEAT -> {
                    val payload = gson.fromJson(message.payload, RepeatPayload::class.java)
                    _incomingCommand.emit(DuoCommand.SetRepeat(RepeatMode.valueOf(payload.mode)))
                }
                MessageType.SYNC_LIBRARY -> handleLibrarySync(message.payload)
                MessageType.SYNC_RESPONSE -> handleSyncResponse(message.payload)
                MessageType.DISCONNECT -> {
                    _incomingCommand.emit(DuoCommand.RequestDisconnect)
                    disconnect()
                }
                // Chat messages
                MessageType.CHAT_MESSAGE -> {
                    val payload = gson.fromJson(message.payload, ChatMessagePayload::class.java)
                    _incomingChatMessage.emit(payload)
                    _isPartnerTyping.value = false
                }
                MessageType.TYPING_START -> {
                    _isPartnerTyping.value = true
                }
                MessageType.TYPING_STOP -> {
                    _isPartnerTyping.value = false
                }
                MessageType.MESSAGE_DELIVERED -> {
                    val payload = gson.fromJson(message.payload, MessageAckPayload::class.java)
                    Log.d(TAG, "Received MESSAGE_DELIVERED for: ${payload.messageId}")
                    _messageDelivered.emit(payload.messageId)
                }
                MessageType.MESSAGE_READ -> {
                    val payload = gson.fromJson(message.payload, MessageAckPayload::class.java)
                    Log.d(TAG, "Received MESSAGE_READ for: ${payload.messageId}")
                    _messageRead.emit(payload.messageId)
                }
                MessageType.VOICE_MESSAGE -> {
                    val payload = gson.fromJson(message.payload, VoiceMessagePayload::class.java)
                    Log.d(TAG, "Received VOICE_MESSAGE from: ${payload.senderName}")
                    _incomingVoiceMessage.emit(payload)
                    _isPartnerTyping.value = false
                }
                else -> Log.w(TAG, "Unhandled message type: ${message.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
        }
    }

    /**
     * Handle library sync request from partner
     */
    private fun handleLibrarySync(payload: String) {
        try {
            val syncPayload = gson.fromJson(payload, SyncLibraryPayload::class.java)
            Log.d(TAG, "Handling library sync with ${syncPayload.songHashes.size} songs from partner")
            Log.d(TAG, "Local songs: ${localSongs.size}, Local hashes: ${localSongHashes.size}")

            if (localSongHashes.isEmpty()) {
                Log.w(TAG, "Local song hashes are empty!")
                // Send empty response
                val response = DuoMessage.createSyncResponse(emptyList())
                webRTCManager.sendMessage(gson.toJson(response))
                return
            }

            // Find common songs by matching hashes
            val partnerHashes = syncPayload.songHashes.map { it.hash }.toSet()
            val commonHashes = localSongHashes.keys.filter { partnerHashes.contains(it) }

            Log.d(TAG, "Partner hashes: ${partnerHashes.size}, Common hashes: ${commonHashes.size}")

            // Update common songs list
            val common = commonHashes.mapNotNull { localSongHashes[it] }
            Log.d(TAG, "Mapped common songs: ${common.size}")

            _commonSongs.value = common
            Log.d(TAG, "Updated _commonSongs to ${_commonSongs.value.size}")

            // Send response with common hashes
            val response = DuoMessage.createSyncResponse(commonHashes)
            val sent = webRTCManager.sendMessage(gson.toJson(response))
            Log.d(TAG, "Sync response sent: $sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling library sync", e)
        }
    }

    /**
     * Handle sync response from partner
     */
    private fun handleSyncResponse(payload: String) {
        try {
            val response = gson.fromJson(payload, SyncResponsePayload::class.java)
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
            _commonSongs.value = common
            Log.d(TAG, "Updated _commonSongs to ${_commonSongs.value.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling sync response", e)
        }
    }

    /**
     * Send library sync to partner
     */
    private fun sendLibrarySync() {
        Log.d(TAG, "Sending library sync with ${localSongs.size} songs")
        if (localSongs.isEmpty()) {
            Log.w(TAG, "No local songs to sync!")
            return
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

        val message = DuoMessage.createSyncLibrary(hashes)
        val success = webRTCManager.sendMessage(gson.toJson(message))
        Log.d(TAG, "Library sync message sent: $success")
    }

    /**
     * Generate hash for a song (same algorithm as DuoRepository)
     */
    private fun generateSongHash(song: Song): String {
        val input = "${song.title}|${song.artist}|${song.duration}"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Set local songs for library sync
     */
    fun setLocalSongs(songs: List<Song>) {
        Log.d(TAG, "Setting local songs: ${songs.size}")
        localSongs = songs
        localSongHashes = songs.associateBy { generateSongHash(it) }
        Log.d(TAG, "Generated ${localSongHashes.size} song hashes")
    }

    /**
     * Get hash for a song
     */
    fun getSongHash(song: Song): String {
        return generateSongHash(song)
    }

    /**
     * Find song by hash
     */
    fun findSongByHash(hash: String): Song? {
        return localSongHashes[hash]
    }

    /**
     * Manually trigger library resync
     */
    fun resyncLibrary() {
        scope.launch {
            Log.d(TAG, "Manual library resync requested")
            sendLibrarySync()
        }
    }

    // Command sending methods

    fun sendPlay(songHash: String, position: Long = 0): Boolean {
        val message = DuoMessage.createPlay(songHash, position)
        return webRTCManager.sendMessage(gson.toJson(message))
    }

    fun sendPause(): Boolean {
        val message = DuoMessage.createPause()
        return webRTCManager.sendMessage(gson.toJson(message))
    }

    fun sendResume(): Boolean {
        val message = DuoMessage.createResume()
        return webRTCManager.sendMessage(gson.toJson(message))
    }

    fun sendSeek(position: Long): Boolean {
        val message = DuoMessage.createSeek(position)
        return webRTCManager.sendMessage(gson.toJson(message))
    }

    fun sendNext(): Boolean {
        val message = DuoMessage.createNext()
        return webRTCManager.sendMessage(gson.toJson(message))
    }

    fun sendPrevious(): Boolean {
        val message = DuoMessage.createPrevious()
        return webRTCManager.sendMessage(gson.toJson(message))
    }

    fun sendShuffle(enabled: Boolean): Boolean {
        val message = DuoMessage.createShuffle(enabled)
        return webRTCManager.sendMessage(gson.toJson(message))
    }

    fun sendRepeat(mode: RepeatMode): Boolean {
        val message = DuoMessage.createRepeat(mode)
        return webRTCManager.sendMessage(gson.toJson(message))
    }
    
    // Chat methods
    
    fun sendChatMessage(message: DuoMessage): Boolean {
        return webRTCManager.sendMessage(gson.toJson(message))
    }
    
    fun sendMessage(message: DuoMessage): Boolean {
        return webRTCManager.sendMessage(gson.toJson(message))
    }
    
    fun sendTypingStart(): Boolean {
        val message = DuoMessage.createTypingStart()
        return webRTCManager.sendMessage(gson.toJson(message))
    }
    
    fun sendTypingStop(): Boolean {
        val message = DuoMessage.createTypingStop()
        return webRTCManager.sendMessage(gson.toJson(message))
    }

    /**
     * Disconnect from partner
     */
    fun disconnect() {
        // Send disconnect message
        val message = DuoMessage.createDisconnect()
        webRTCManager.sendMessage(gson.toJson(message))
        
        // Clear signaling data
        myDuoId?.let { signalingManager.clearSignalingData(it) }
        
        webRTCManager.disconnect()
        
        partnerId = null
        partnerDeviceName = null
        _partnerPresence.value = null
        _connectionState.value = WebRTCConnectionState.Idle
        _commonSongs.value = emptyList()
        
        answerJob?.cancel()
    }

    /**
     * Format last seen time
     */
    fun formatLastSeen(timestamp: Long): String {
        return presenceManager.formatLastSeen(timestamp)
    }

    /**
     * Check if connected via WebRTC
     */
    fun isConnected(): Boolean {
        return _connectionState.value is WebRTCConnectionState.Connected
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        disconnect()
        presenceManager.stopPresenceTracking()
        signalingManager.cleanup()
        webRTCManager.cleanup()
        incomingOfferJob?.cancel()
        answerJob?.cancel()
    }
}
