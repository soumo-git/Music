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
    data class Pending(val request: ConnectionRequest) : IncomingRequestState()
}
