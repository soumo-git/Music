package com.android.music.duo.chat.repository

import android.os.Build
import android.util.Log
import com.android.music.duo.chat.model.ChatMessage
import com.android.music.duo.chat.model.MessageStatus
import com.android.music.duo.data.model.ChatMessagePayload
import com.android.music.duo.data.model.DuoMessage
import com.android.music.duo.data.model.MessageType
import com.android.music.duo.service.DuoSocketManager
import com.android.music.duo.webrtc.WebRTCManager
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

/**
 * Repository for handling Duo chat messages
 * Works over both WiFi Direct (socket) and WebRTC (data channel)
 */
class ChatRepository(
    private val socketManager: DuoSocketManager,
    private val webRTCManager: WebRTCManager
) {
    companion object {
        private const val TAG = "ChatRepository"
        private const val TYPING_DEBOUNCE_MS = 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val deviceName = Build.MODEL

    // Messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Partner typing state
    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping: StateFlow<Boolean> = _isPartnerTyping.asStateFlow()

    // Incoming message event
    private val _incomingMessage = MutableSharedFlow<ChatMessage>()
    val incomingMessage: SharedFlow<ChatMessage> = _incomingMessage.asSharedFlow()

    // Typing state management
    private var typingJob: Job? = null
    private var isCurrentlyTyping = false

    /**
     * Send a chat message
     */
    fun sendMessage(text: String): ChatMessage {
        val message = ChatMessage(
            text = text.trim(),
            senderName = deviceName,
            isFromMe = true,
            status = MessageStatus.SENDING
        )

        // Add to local messages
        _messages.value = _messages.value + message

        // Send via appropriate channel
        scope.launch {
            val duoMessage = DuoMessage.createChatMessage(message.id, message.text, message.senderName)
            val success = sendDuoMessage(duoMessage)

            // Update message status
            val updatedStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
            updateMessageStatus(message.id, updatedStatus)
        }

        // Stop typing indicator
        stopTyping()

        return message
    }

    /**
     * Handle incoming chat message from partner
     */
    fun handleIncomingChatMessage(payload: String) {
        try {
            val chatPayload = gson.fromJson(payload, ChatMessagePayload::class.java)
            val message = ChatMessage(
                id = chatPayload.messageId,
                text = chatPayload.text,
                senderName = chatPayload.senderName,
                isFromMe = false,
                status = MessageStatus.DELIVERED
            )

            Log.d(TAG, "Received chat message: ${message.text}")
            _messages.value = _messages.value + message

            scope.launch {
                _incomingMessage.emit(message)
            }

            // Partner stopped typing when they send a message
            _isPartnerTyping.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing chat message", e)
        }
    }

    /**
     * Handle typing start from partner
     */
    fun handleTypingStart() {
        _isPartnerTyping.value = true
    }

    /**
     * Handle typing stop from partner
     */
    fun handleTypingStop() {
        _isPartnerTyping.value = false
    }

    /**
     * Notify partner that we started typing
     */
    fun startTyping() {
        if (!isCurrentlyTyping) {
            isCurrentlyTyping = true
            scope.launch {
                sendDuoMessage(DuoMessage.createTypingStart())
            }
        }

        // Reset typing timeout
        typingJob?.cancel()
        typingJob = scope.launch {
            delay(TYPING_DEBOUNCE_MS * 3)
            stopTyping()
        }
    }

    /**
     * Notify partner that we stopped typing
     */
    fun stopTyping() {
        if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            typingJob?.cancel()
            scope.launch {
                sendDuoMessage(DuoMessage.createTypingStop())
            }
        }
    }

    /**
     * Clear all messages (on disconnect)
     */
    fun clearMessages() {
        _messages.value = emptyList()
        _isPartnerTyping.value = false
    }

    /**
     * Update message status
     */
    private fun updateMessageStatus(messageId: String, status: MessageStatus) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) msg.copy(status = status) else msg
        }
    }

    /**
     * Send message via appropriate channel (WebRTC or WiFi Direct)
     */
    private suspend fun sendDuoMessage(message: DuoMessage): Boolean {
        return try {
            // Try WebRTC first if data channel is open
            if (webRTCManager.dataChannelOpen.value) {
                webRTCManager.sendMessage(message.toJson())
            } else if (socketManager.isConnected()) {
                socketManager.sendMessage(message)
            } else {
                Log.w(TAG, "No connection available to send message")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }
}
