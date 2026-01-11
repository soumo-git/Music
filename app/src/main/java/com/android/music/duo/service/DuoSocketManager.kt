package com.android.music.duo.service

import android.util.Log
import com.android.music.duo.data.model.DuoMessage
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
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Manages socket communication between Duo devices
 */
class DuoSocketManager {

    companion object {
        private const val TAG = "DuoSocketManager"
        const val PORT = 8888
        private const val SOCKET_TIMEOUT = 5000
        private const val PING_INTERVAL_MS = 3000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private val _incomingMessages = MutableSharedFlow<DuoMessage>()
    val incomingMessages: SharedFlow<DuoMessage> = _incomingMessages.asSharedFlow()

    private val _connectionStatus = MutableSharedFlow<ConnectionStatus>(replay = 1)
    val connectionStatus: SharedFlow<ConnectionStatus> = _connectionStatus.asSharedFlow()
    
    // Connection quality (0-100)
    private val _connectionQuality = MutableStateFlow(0)
    val connectionQuality: StateFlow<Int> = _connectionQuality.asStateFlow()
    
    // Ping tracking
    private var lastPingSentTime = 0L
    private var pingJob: Job? = null

    private var isRunning = false

    /**
     * Start as server (host)
     */
    suspend fun startServer() = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(PORT)
            _connectionStatus.emit(ConnectionStatus.WaitingForClient)
            Log.d(TAG, "Server started on port $PORT, waiting for client...")

            isRunning = true
            while (isRunning) {
                try {
                    Log.d(TAG, "Waiting for client connection...")
                    val socket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Client accepted!")
                    handleClientConnection(socket)
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error accepting connection", e)
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server", e)
            _connectionStatus.emit(ConnectionStatus.Error(e.message ?: "Server error"))
        }
    }

    /**
     * Connect as client (guest)
     */
    suspend fun connectToServer(hostAddress: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to server at $hostAddress:$PORT...")
            _connectionStatus.emit(ConnectionStatus.Connecting)
            
            isRunning = true // Set before connecting
            
            clientSocket = Socket()
            clientSocket?.connect(InetSocketAddress(hostAddress, PORT), SOCKET_TIMEOUT)
            
            clientSocket?.let { socket ->
                setupStreams(socket)
                Log.d(TAG, "Connected to server! Emitting Connected status...")
                _connectionStatus.emit(ConnectionStatus.Connected)
                Log.d(TAG, "Connected status emitted, starting listener...")
                startPingMonitoring()
                startListening()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to server: ${e.message}", e)
            isRunning = false
            _connectionStatus.emit(ConnectionStatus.Error(e.message ?: "Connection error"))
        }
    }

    private suspend fun handleClientConnection(socket: Socket) {
        clientSocket = socket
        setupStreams(socket)
        Log.d(TAG, "Client connected from: ${socket.inetAddress}, emitting Connected status...")
        _connectionStatus.emit(ConnectionStatus.Connected)
        Log.d(TAG, "Connected status emitted, starting listener...")
        startPingMonitoring()
        startListening()
    }

    private fun setupStreams(socket: Socket) {
        writer = PrintWriter(socket.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    }
    
    /**
     * Start ping monitoring for connection quality
     */
    private fun startPingMonitoring() {
        stopPingMonitoring()
        pingJob = scope.launch {
            while (isRunning && isConnected()) {
                sendPing()
                delay(PING_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop ping monitoring
     */
    private fun stopPingMonitoring() {
        pingJob?.cancel()
        pingJob = null
    }
    
    /**
     * Send a ping message
     */
    private fun sendPing() {
        try {
            lastPingSentTime = System.currentTimeMillis()
            val pingMessage = DuoMessage.createPing()
            writer?.println(pingMessage.toJson())
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending ping", e)
        }
    }
    
    /**
     * Handle received pong and calculate latency
     */
    private fun handlePong() {
        if (lastPingSentTime > 0) {
            val rtt = System.currentTimeMillis() - lastPingSentTime
            val quality = calculateQualityFromRtt(rtt)
            _connectionQuality.value = quality
            Log.d(TAG, "Ping RTT: ${rtt}ms, Quality: $quality")
        }
    }
    
    /**
     * Calculate quality score from RTT
     */
    private fun calculateQualityFromRtt(rtt: Long): Int {
        return when {
            rtt < 50 -> 100   // Excellent
            rtt < 100 -> 85   // Very good
            rtt < 200 -> 70   // Good
            rtt < 400 -> 50   // Fair
            rtt < 800 -> 30   // Poor
            else -> 10        // Very poor
        }
    }

    private suspend fun startListening() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to listen for messages...")
            while (isRunning && clientSocket?.isConnected == true) {
                val line = reader?.readLine() ?: break
                Log.d(TAG, "Received raw message: ${line.take(200)}...") // Log first 200 chars
                DuoMessage.fromJson(line)?.let { message ->
                    // Handle ping/pong internally
                    when (message.type) {
                        com.android.music.duo.data.model.MessageType.PING -> {
                            // Respond with pong
                            val pongMessage = DuoMessage.createPong()
                            writer?.println(pongMessage.toJson())
                            writer?.flush()
                        }
                        com.android.music.duo.data.model.MessageType.PONG -> {
                            // Calculate latency
                            handlePong()
                        }
                        else -> {
                            Log.d(TAG, "Parsed message type: ${message.type}")
                            _incomingMessages.emit(message)
                        }
                    }
                } ?: Log.e(TAG, "Failed to parse message")
            }
            Log.d(TAG, "Stopped listening - isRunning=$isRunning, isConnected=${clientSocket?.isConnected}")
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error reading message", e)
                _connectionStatus.emit(ConnectionStatus.Disconnected)
            }
        }
    }

    /**
     * Send a message to the connected device
     */
    suspend fun sendMessage(message: DuoMessage): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = message.toJson()
            Log.d(TAG, "Sending message type: ${message.type}, payload length: ${message.payload.length}")
            writer?.println(json)
            writer?.flush()
            Log.d(TAG, "Message sent successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }

    /**
     * Close all connections
     */
    fun disconnect() {
        isRunning = false
        stopPingMonitoring()
        _connectionQuality.value = 0
        try {
            writer?.close()
            reader?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connections", e)
        }
        writer = null
        reader = null
        clientSocket = null
        serverSocket = null
    }

    fun isConnected(): Boolean {
        return clientSocket?.isConnected == true && !clientSocket!!.isClosed
    }
}

sealed class ConnectionStatus {
    object WaitingForClient : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
    object Disconnected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}
