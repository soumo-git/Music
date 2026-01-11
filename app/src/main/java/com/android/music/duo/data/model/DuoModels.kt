package com.android.music.duo.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a device discovered via WiFi Direct
 */
@Parcelize
data class DuoDevice(
    val deviceName: String,
    val deviceAddress: String,
    val isGroupOwner: Boolean = false,
    val status: DeviceStatus = DeviceStatus.AVAILABLE
) : Parcelable

enum class DeviceStatus {
    AVAILABLE,
    INVITED,
    CONNECTED,
    FAILED,
    UNAVAILABLE
}

/**
 * Connection state for Duo
 */
sealed class DuoConnectionState {
    object Disconnected : DuoConnectionState()
    object Searching : DuoConnectionState()
    data class Connecting(val device: DuoDevice) : DuoConnectionState()
    data class Connected(val device: DuoDevice, val isHost: Boolean) : DuoConnectionState()
    data class Error(val message: String) : DuoConnectionState()
}

/**
 * Signal strength indicator
 */
enum class SignalStrength() {
    NONE,
    WEAK,
    FAIR,
    GOOD,
    EXCELLENT
}

/**
 * Commands sent between devices
 */
sealed class DuoCommand {
    data class Play(val songHash: String, val position: Long = 0) : DuoCommand()
    object Pause : DuoCommand()
    object Resume : DuoCommand()
    data class Seek(val position: Long) : DuoCommand()
    object Next : DuoCommand()
    object Previous : DuoCommand()
    data class SetShuffle(val enabled: Boolean) : DuoCommand()
    data class SetRepeat(val mode: RepeatMode) : DuoCommand()
    data class AddToQueue(val songHashes: List<String>) : DuoCommand()
    object ClearQueue : DuoCommand()
    object RequestDisconnect : DuoCommand()
}

enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

/**
 * Song hash for comparing libraries between devices
 */
data class SongHash(
    val id: Long,
    val hash: String, // MD5 of title + artist + duration
    val title: String,
    val artist: String,
    val duration: Long
)

/**
 * Sort options for Duo song list
 */
enum class DuoSortOption {
    NAME,
    TIME,
    DURATION
}
