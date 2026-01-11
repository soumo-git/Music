package com.android.music.duo.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.coroutines.resume

/**
 * Manages WebRTC peer connection and data channel for Duo communication
 * Uses non-trickle ICE (all candidates gathered before sending SDP)
 */
class WebRTCManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRTCManager"
        private const val DATA_CHANNEL_LABEL = "duo_channel"
        private const val ICE_GATHERING_POLL_INTERVAL_MS = 100L
        private const val ICE_GATHERING_TIMEOUT_MS = 5000L // 5 seconds max wait
        
        // Reliable STUN servers
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    // Connection state
    private val _connectionState = MutableStateFlow<WebRTCConnectionState>(WebRTCConnectionState.Idle)
    val connectionState: StateFlow<WebRTCConnectionState> = _connectionState.asStateFlow()

    // Incoming messages from data channel
    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    // Data channel state
    private val _dataChannelOpen = MutableStateFlow(false)
    val dataChannelOpen: StateFlow<Boolean> = _dataChannelOpen.asStateFlow()
    
    // Connection quality (0-100, based on RTT and packet loss)
    private val _connectionQuality = MutableStateFlow(0)
    val connectionQuality: StateFlow<Int> = _connectionQuality.asStateFlow()
    
    // Stats polling job
    private var statsPollingRunnable: Runnable? = null

    sealed class WebRTCConnectionState {
        object Idle : WebRTCConnectionState()
        object Initializing : WebRTCConnectionState()
        object GatheringCandidates : WebRTCConnectionState()
        object Connecting : WebRTCConnectionState()
        object Connected : WebRTCConnectionState()
        data class Error(val message: String) : WebRTCConnectionState()
        object Disconnected : WebRTCConnectionState()
    }

    /**
     * Initialize WebRTC
     */
    fun initialize() {
        if (peerConnectionFactory != null) {
            Log.d(TAG, "Already initialized")
            return
        }

        Log.d(TAG, "Initializing WebRTC...")
        _connectionState.value = WebRTCConnectionState.Initializing

        // Initialize PeerConnectionFactory
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        Log.d(TAG, "WebRTC initialized")
        _connectionState.value = WebRTCConnectionState.Idle
    }

    /**
     * Create peer connection
     */
    private fun createPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // CRITICAL: Use GATHER_ONCE to ensure all candidates are gathered before completing
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            // Disable TCP candidates to speed up gathering
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE connection state: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        _connectionState.value = WebRTCConnectionState.Connected
                        // Start polling connection stats
                        startStatsPolling()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> {
                        _connectionState.value = WebRTCConnectionState.Disconnected
                        stopStatsPolling()
                        _connectionQuality.value = 0
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        _connectionState.value = WebRTCConnectionState.Idle
                        stopStatsPolling()
                        _connectionQuality.value = 0
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "ICE receiving: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state: $state")
                // Note: We use polling instead of relying on this callback
                // because COMPLETE state isn't always triggered reliably
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "ICE candidate: ${it.sdp}")
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Log.d(TAG, "ICE candidates removed")
            }

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "Stream added")
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "Stream removed")
            }

            override fun onDataChannel(channel: DataChannel?) {
                Log.d(TAG, "Data channel received: ${channel?.label()}")
                channel?.let { setupDataChannel(it) }
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "Track added")
            }
        }

        return peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
    }

    /**
     * Wait for ICE gathering to complete using polling approach
     * This is more reliable than relying on onIceGatheringChange callback
     * Includes timeout fallback - if gathering takes too long, use what we have
     */
    private fun waitForIceGatheringComplete(pc: PeerConnection, onComplete: () -> Unit) {
        val startTime = System.currentTimeMillis()
        
        if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
            Log.d(TAG, "ICE gathering already complete")
            onComplete()
            return
        }

        val checkComplete = object : Runnable {
            override fun run() {
                val state = pc.iceGatheringState()
                val elapsed = System.currentTimeMillis() - startTime
                
                Log.d(TAG, "Polling ICE gathering state: $state (elapsed: ${elapsed}ms)")
                
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    Log.d(TAG, "ICE gathering complete (via polling)")
                    onComplete()
                } else if (elapsed >= ICE_GATHERING_TIMEOUT_MS) {
                    // Timeout - use what we have
                    Log.w(TAG, "ICE gathering timeout after ${elapsed}ms, using current candidates")
                    onComplete()
                } else {
                    // Poll again after a short delay
                    mainHandler.postDelayed(this, ICE_GATHERING_POLL_INTERVAL_MS)
                }
            }
        }
        mainHandler.post(checkComplete)
    }

    /**
     * Setup data channel observers
     */
    private fun setupDataChannel(channel: DataChannel) {
        dataChannel = channel
        
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {
                Log.d(TAG, "Buffered amount: $amount")
            }

            override fun onStateChange() {
                val state = channel.state()
                Log.d(TAG, "Data channel state: $state")
                _dataChannelOpen.value = state == DataChannel.State.OPEN
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer?.let {
                    val data = ByteArray(it.data.remaining())
                    it.data.get(data)
                    val message = String(data, Charset.forName("UTF-8"))
                    Log.d(TAG, "Received message: $message")
                    scope.launch {
                        _incomingMessages.emit(message)
                    }
                }
            }
        })
    }

    /**
     * Create SDP offer with all ICE candidates (non-trickle)
     * Returns the complete SDP string or null on failure
     */
    suspend fun createOffer(): String? {
        initialize()
        
        peerConnection?.close()
        peerConnection = createPeerConnection()
        
        if (peerConnection == null) {
            Log.e(TAG, "Failed to create peer connection")
            return null
        }

        _connectionState.value = WebRTCConnectionState.GatheringCandidates

        // Create data channel (only offerer creates it)
        val dcInit = DataChannel.Init().apply {
            ordered = true
            negotiated = false
        }
        dataChannel = peerConnection?.createDataChannel(DATA_CHANNEL_LABEL, dcInit)
        dataChannel?.let { setupDataChannel(it) }

        // Create offer with non-trickle ICE constraint (CRITICAL: TrickleIce = false)
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("TrickleIce", "false"))
        }

        return suspendCancellableCoroutine { cont ->
            val pc = peerConnection ?: return@suspendCancellableCoroutine cont.resume(null)
            
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    Log.d(TAG, "Offer created, setting local description...")
                    sdp?.let {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description set, waiting for ICE gathering...")
                                // Use polling to wait for ICE gathering to complete
                                waitForIceGatheringComplete(pc) {
                                    val localDesc = pc.localDescription
                                    if (localDesc != null) {
                                        Log.d(TAG, "ICE gathering complete, SDP length: ${localDesc.description.length}")
                                        cont.resume(localDesc.description)
                                    } else {
                                        Log.e(TAG, "Local description is null after ICE gathering!")
                                        cont.resume(null)
                                    }
                                }
                            }
                            override fun onCreateFailure(error: String?) {
                                Log.e(TAG, "Set local description create failure: $error")
                                cont.resume(null)
                            }
                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "Set local description failure: $error")
                                cont.resume(null)
                            }
                        }, it)
                    } ?: cont.resume(null)
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create offer failure: $error")
                    cont.resume(null)
                }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
    }

    /**
     * Create SDP answer from received offer (non-trickle)
     * Returns the complete SDP string or null on failure
     */
    suspend fun createAnswer(offerSdp: String): String? {
        initialize()
        
        peerConnection?.close()
        peerConnection = createPeerConnection()
        
        if (peerConnection == null) {
            Log.e(TAG, "Failed to create peer connection")
            return null
        }

        _connectionState.value = WebRTCConnectionState.GatheringCandidates

        // Parse and set remote description (offer)
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        
        // Set remote description first
        val remoteSet = suspendCancellableCoroutine { cont ->
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote description set successfully")
                    cont.resume(true)
                }
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Set remote description create failure: $error")
                    cont.resume(false)
                }
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set remote description failure: $error")
                    cont.resume(false)
                }
            }, remoteDesc)
        }
        
        if (!remoteSet) {
            return null
        }

        // Create answer with non-trickle ICE (CRITICAL: TrickleIce = false)
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("TrickleIce", "false"))
        }

        return suspendCancellableCoroutine { cont ->
            val pc = peerConnection ?: return@suspendCancellableCoroutine cont.resume(null)
            
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    Log.d(TAG, "Answer created, setting local description...")
                    sdp?.let {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description set, waiting for ICE gathering...")
                                // Use polling to wait for ICE gathering to complete
                                waitForIceGatheringComplete(pc) {
                                    val localDesc = pc.localDescription
                                    if (localDesc != null) {
                                        Log.d(TAG, "ICE gathering complete, SDP length: ${localDesc.description.length}")
                                        cont.resume(localDesc.description)
                                    } else {
                                        Log.e(TAG, "Local description is null after ICE gathering!")
                                        cont.resume(null)
                                    }
                                }
                            }
                            override fun onCreateFailure(error: String?) {
                                Log.e(TAG, "Set local description create failure: $error")
                                cont.resume(null)
                            }
                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "Set local description failure: $error")
                                cont.resume(null)
                            }
                        }, it)
                    } ?: cont.resume(null)
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create answer failure: $error")
                    cont.resume(null)
                }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
    }

    /**
     * Set remote answer SDP
     */
    suspend fun setRemoteAnswer(answerSdp: String): Boolean {
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        
        return suspendCancellableCoroutine { cont ->
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote answer set successfully")
                    _connectionState.value = WebRTCConnectionState.Connecting
                    cont.resume(true)
                }
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Set remote answer create failure: $error")
                    cont.resume(false)
                }
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set remote answer failure: $error")
                    _connectionState.value = WebRTCConnectionState.Error("Failed to set remote answer: $error")
                    cont.resume(false)
                }
            }, remoteDesc)
        }
    }

    /**
     * Start polling connection stats for quality monitoring
     */
    private fun startStatsPolling() {
        stopStatsPolling()
        
        statsPollingRunnable = object : Runnable {
            override fun run() {
                peerConnection?.getStats { report ->
                    var rtt = -1.0
                    var packetsLost = 0L
                    var packetsReceived = 0L
                    
                    report.statsMap.values.forEach { stats ->
                        when (stats.type) {
                            "candidate-pair" -> {
                                // Get RTT from candidate pair stats
                                stats.members["currentRoundTripTime"]?.let { value ->
                                    rtt = (value as? Double) ?: -1.0
                                }
                            }
                            "inbound-rtp" -> {
                                // Get packet loss info
                                stats.members["packetsLost"]?.let { value ->
                                    packetsLost += ((value as? Number)?.toLong() ?: 0L)
                                }
                                stats.members["packetsReceived"]?.let { value ->
                                    packetsReceived += ((value as? Number)?.toLong() ?: 0L)
                                }
                            }
                        }
                    }
                    
                    // Calculate quality score (0-100)
                    val quality = calculateQualityScore(rtt, packetsLost, packetsReceived)
                    _connectionQuality.value = quality
                    Log.d(TAG, "Connection quality: $quality (RTT: ${rtt}s, lost: $packetsLost, received: $packetsReceived)")
                }
                
                // Poll every 3 seconds
                mainHandler.postDelayed(this, 3000)
            }
        }
        mainHandler.post(statsPollingRunnable!!)
    }
    
    /**
     * Stop polling connection stats
     */
    private fun stopStatsPolling() {
        statsPollingRunnable?.let { mainHandler.removeCallbacks(it) }
        statsPollingRunnable = null
    }
    
    /**
     * Calculate quality score based on RTT and packet loss
     */
    private fun calculateQualityScore(rtt: Double, packetsLost: Long, packetsReceived: Long): Int {
        // If no data yet, assume good quality
        if (rtt < 0 && packetsReceived == 0L) {
            return 80 // Default to good
        }
        
        var score = 100
        
        // RTT scoring (in seconds)
        if (rtt >= 0) {
            score -= when {
                rtt > 0.5 -> 50  // Very high latency
                rtt > 0.3 -> 30  // High latency
                rtt > 0.15 -> 15 // Medium latency
                rtt > 0.05 -> 5  // Low latency
                else -> 0        // Excellent
            }
        }
        
        // Packet loss scoring
        if (packetsReceived > 0) {
            val lossRate = packetsLost.toDouble() / (packetsLost + packetsReceived)
            score -= when {
                lossRate > 0.1 -> 40  // >10% loss
                lossRate > 0.05 -> 25 // >5% loss
                lossRate > 0.02 -> 15 // >2% loss
                lossRate > 0.01 -> 5  // >1% loss
                else -> 0
            }
        }
        
        return score.coerceIn(0, 100)
    }

    /**
     * Send message through data channel
     */
    fun sendMessage(message: String): Boolean {
        if (dataChannel?.state() != DataChannel.State.OPEN) {
            Log.w(TAG, "Data channel not open, cannot send message")
            return false
        }

        val buffer = ByteBuffer.wrap(message.toByteArray(Charset.forName("UTF-8")))
        val dataBuffer = DataChannel.Buffer(buffer, false)
        val sent = dataChannel?.send(dataBuffer) ?: false
        Log.d(TAG, "Message sent: $sent")
        return sent
    }

    /**
     * Close connection
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        
        // Stop stats polling
        stopStatsPolling()
        
        // Remove any pending polling callbacks
        mainHandler.removeCallbacksAndMessages(null)
        
        dataChannel?.close()
        dataChannel = null
        
        peerConnection?.close()
        peerConnection = null
        
        _connectionState.value = WebRTCConnectionState.Idle
        _dataChannelOpen.value = false
        _connectionQuality.value = 0
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        // Remove any pending polling callbacks
        mainHandler.removeCallbacksAndMessages(null)
        disconnect()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }
}
