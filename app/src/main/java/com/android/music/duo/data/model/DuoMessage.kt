package com.android.music.duo.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * JSON message format for WiFi Direct communication
 */
data class DuoMessage(
    @SerializedName("type") val type: MessageType,
    @SerializedName("payload") val payload: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): DuoMessage? {
            return try {
                gson.fromJson(json, DuoMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun createPlay(songHash: String, position: Long = 0): DuoMessage {
            return DuoMessage(
                type = MessageType.PLAY,
                payload = gson.toJson(PlayPayload(songHash, position))
            )
        }

        fun createPause(): DuoMessage {
            return DuoMessage(type = MessageType.PAUSE, payload = "")
        }

        fun createResume(): DuoMessage {
            return DuoMessage(type = MessageType.RESUME, payload = "")
        }

        fun createSeek(position: Long): DuoMessage {
            return DuoMessage(
                type = MessageType.SEEK,
                payload = gson.toJson(SeekPayload(position))
            )
        }

        fun createNext(): DuoMessage {
            return DuoMessage(type = MessageType.NEXT, payload = "")
        }

        fun createPrevious(): DuoMessage {
            return DuoMessage(type = MessageType.PREVIOUS, payload = "")
        }

        fun createShuffle(enabled: Boolean): DuoMessage {
            return DuoMessage(
                type = MessageType.SHUFFLE,
                payload = gson.toJson(ShufflePayload(enabled))
            )
        }

        fun createRepeat(mode: RepeatMode): DuoMessage {
            return DuoMessage(
                type = MessageType.REPEAT,
                payload = gson.toJson(RepeatPayload(mode.name))
            )
        }

        fun createAddToQueue(songHashes: List<String>): DuoMessage {
            return DuoMessage(
                type = MessageType.ADD_TO_QUEUE,
                payload = gson.toJson(QueuePayload(songHashes))
            )
        }

        fun createClearQueue(): DuoMessage {
            return DuoMessage(type = MessageType.CLEAR_QUEUE, payload = "")
        }

        fun createSyncLibrary(songHashes: List<SongHash>): DuoMessage {
            return DuoMessage(
                type = MessageType.SYNC_LIBRARY,
                payload = gson.toJson(SyncLibraryPayload(songHashes))
            )
        }

        fun createSyncResponse(commonHashes: List<String>): DuoMessage {
            return DuoMessage(
                type = MessageType.SYNC_RESPONSE,
                payload = gson.toJson(SyncResponsePayload(commonHashes))
            )
        }

        fun createConnectionRequest(deviceName: String, userId: String): DuoMessage {
            return DuoMessage(
                type = MessageType.CONNECTION_REQUEST,
                payload = gson.toJson(ConnectionPayload(deviceName, userId))
            )
        }

        fun createConnectionAccept(): DuoMessage {
            return DuoMessage(type = MessageType.CONNECTION_ACCEPT, payload = "")
        }

        fun createConnectionReject(): DuoMessage {
            return DuoMessage(type = MessageType.CONNECTION_REJECT, payload = "")
        }

        fun createDisconnect(): DuoMessage {
            return DuoMessage(type = MessageType.DISCONNECT, payload = "")
        }
    }

    fun toJson(): String = gson.toJson(this)
}

enum class MessageType {
    PLAY,
    PAUSE,
    RESUME,
    SEEK,
    NEXT,
    PREVIOUS,
    SHUFFLE,
    REPEAT,
    ADD_TO_QUEUE,
    CLEAR_QUEUE,
    SYNC_LIBRARY,
    SYNC_RESPONSE,
    CONNECTION_REQUEST,
    CONNECTION_ACCEPT,
    CONNECTION_REJECT,
    DISCONNECT,
    HEARTBEAT
}

// Payload classes
data class PlayPayload(val songHash: String, val position: Long)
data class SeekPayload(val position: Long)
data class ShufflePayload(val enabled: Boolean)
data class RepeatPayload(val mode: String)
data class QueuePayload(val songHashes: List<String>)
data class SyncLibraryPayload(val songHashes: List<SongHash>)
data class SyncResponsePayload(val commonHashes: List<String>)
data class ConnectionPayload(val deviceName: String, val userId: String)
