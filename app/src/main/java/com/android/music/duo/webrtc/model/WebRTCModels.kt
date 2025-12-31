package com.android.music.duo.webrtc.model

/**
 * Represents a Duo user in Firebase
 */
data class DuoUser(
    val duoId: String = "",
    val googleUid: String? = null,
    val deviceName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Presence status for a user
 */
data class PresenceStatus(
    val online: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val deviceName: String = ""
)

/**
 * WebRTC signaling offer/answer
 */
data class SignalingData(
    val type: String = "", // "offer" or "answer"
    val sdp: String = "",
    val fromId: String = "",
    val fromDeviceName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Connection request stored in Firebase
 */
data class ConnectionRequest(
    val fromId: String = "",
    val fromDeviceName: String = "",
    val offer: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "pending" // pending, accepted, rejected, expired
)

/**
 * Connection response
 */
data class ConnectionResponse(
    val fromId: String = "",
    val answer: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Notify request when user wants to be notified when partner comes online
 */
data class NotifyRequest(
    val requesterId: String = "",
    val requesterDeviceName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * WebRTC connection state
 */
sealed class WebRTCConnectionState {
    object Idle : WebRTCConnectionState()
    object CheckingPartner : WebRTCConnectionState()
    data class PartnerOffline(val lastSeen: Long, val deviceName: String) : WebRTCConnectionState()
    object CreatingOffer : WebRTCConnectionState()
    object WaitingForAnswer : WebRTCConnectionState()
    object Connecting : WebRTCConnectionState()
    data class Connected(val partnerId: String, val partnerDeviceName: String) : WebRTCConnectionState()
    data class Error(val message: String) : WebRTCConnectionState()
    object Disconnected : WebRTCConnectionState()
}

/**
 * Incoming connection request state
 */
sealed class IncomingRequestState {
    object None : IncomingRequestState()
    data class Pending(val request: ConnectionRequest) : IncomingRequestState()
    object Accepted : IncomingRequestState()
    object Rejected : IncomingRequestState()
}
