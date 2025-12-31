package com.android.music.duo.service

import android.util.Log
import com.android.music.duo.data.model.DuoMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private val _incomingMessages = MutableSharedFlow<DuoMessage>()
    val incomingMessages: SharedFlow<DuoMessage> = _incomingMessages.asSharedFlow()

    private val _connectionStatus = MutableSharedFlow<ConnectionStatus>(replay = 1)
    val connectionStatus: SharedFlow<ConnectionStatus> = _connectionStatus.asSharedFlow()

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
        startListening()
    }

    private fun setupStreams(socket: Socket) {
        writer = PrintWriter(socket.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    }

    private suspend fun startListening() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to listen for messages...")
            while (isRunning && clientSocket?.isConnected == true) {
                val line = reader?.readLine() ?: break
                Log.d(TAG, "Received raw message: ${line.take(200)}...") // Log first 200 chars
                DuoMessage.fromJson(line)?.let { message ->
                    Log.d(TAG, "Parsed message type: ${message.type}")
                    _incomingMessages.emit(message)
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
    object Idle : ConnectionStatus()
    object WaitingForClient : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
    object Disconnected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}
