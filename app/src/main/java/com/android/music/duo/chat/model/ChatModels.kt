package com.android.music.duo.chat.model

import java.util.UUID

/**
 * Represents a chat message in Duo chat
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val senderName: String,
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENDING,
    val type: MessageType = MessageType.TEXT,
    val voiceDuration: Long = 0L, // Duration in milliseconds for voice messages
    val voiceData: ByteArray? = null // Audio data for voice messages
) {
    // Use default data class equals/hashCode for proper change detection
    // Only override for voiceData ByteArray comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChatMessage
        if (id != other.id) return false
        if (text != other.text) return false
        if (senderName != other.senderName) return false
        if (isFromMe != other.isFromMe) return false
        if (timestamp != other.timestamp) return false
        if (status != other.status) return false
        if (type != other.type) return false
        if (voiceDuration != other.voiceDuration) return false
        if (voiceData != null) {
            if (other.voiceData == null) return false
            if (!voiceData.contentEquals(other.voiceData)) return false
        } else if (other.voiceData != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + senderName.hashCode()
        result = 31 * result + isFromMe.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + voiceDuration.hashCode()
        result = 31 * result + (voiceData?.contentHashCode() ?: 0)
        return result
    }
}

enum class MessageStatus {
    SENDING,    // Clock icon
    SENT,       // Single tick (gray)
    DELIVERED,  // Double tick (gray)
    READ,       // Double tick (blue)
    FAILED      // Error icon
}

enum class MessageType {
    TEXT,
    VOICE
}

/**
 * Chat state for UI
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isPartnerTyping: Boolean = false,
    val connectionType: String = "",
    val signalStrength: Int = 0, // 0-4 bars
    val isConnected: Boolean = false,
    val hasUnreadMessages: Boolean = false
)
