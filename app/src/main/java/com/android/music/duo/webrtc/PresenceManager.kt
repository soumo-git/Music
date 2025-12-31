package com.android.music.duo.webrtc

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.music.config.FirebaseConfig
import com.android.music.duo.webrtc.model.PresenceStatus
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages user presence (online/offline status) in Firebase RTDB
 */
class PresenceManager(private val context: Context) {

    companion object {
        private const val TAG = "PresenceManager"
        private const val PRESENCE_PATH = "presence"
        
        @Volatile
        private var instance: PresenceManager? = null
        
        fun getInstance(context: Context): PresenceManager {
            return instance ?: synchronized(this) {
                instance ?: PresenceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val database = FirebaseDatabase.getInstance(FirebaseConfig.RTDB_URL)
    private val presenceRef = database.getReference(PRESENCE_PATH)
    private var myPresenceRef: DatabaseReference? = null
    private var connectedRef: DatabaseReference? = null
    private var connectionListener: ValueEventListener? = null
    
    private var currentDuoId: String? = null

    /**
     * Start tracking presence for the given Duo ID
     * Sets online when connected, offline when disconnected
     */
    fun startPresenceTracking(duoId: String) {
        currentDuoId = duoId
        myPresenceRef = presenceRef.child(duoId)
        connectedRef = database.getReference(".info/connected")

        val presenceData = mapOf(
            "online" to true,
            "lastSeen" to ServerValue.TIMESTAMP,
            "deviceName" to Build.MODEL
        )

        val offlineData = mapOf(
            "online" to false,
            "lastSeen" to ServerValue.TIMESTAMP,
            "deviceName" to Build.MODEL
        )

        connectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "Firebase connection state: $connected")
                
                if (connected) {
                    // When we disconnect, set offline status
                    myPresenceRef?.onDisconnect()?.setValue(offlineData)
                    // Set online status now
                    myPresenceRef?.setValue(presenceData)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Connection listener cancelled", error.toException())
            }
        }

        connectedRef?.addValueEventListener(connectionListener!!)
        Log.d(TAG, "Started presence tracking for $duoId")
    }

    /**
     * Stop presence tracking and set offline
     */
    fun stopPresenceTracking() {
        connectionListener?.let { listener ->
            connectedRef?.removeEventListener(listener)
        }
        
        // Set offline immediately
        myPresenceRef?.setValue(mapOf(
            "online" to false,
            "lastSeen" to ServerValue.TIMESTAMP,
            "deviceName" to Build.MODEL
        ))
        
        myPresenceRef = null
        connectedRef = null
        connectionListener = null
        currentDuoId = null
        
        Log.d(TAG, "Stopped presence tracking")
    }

    /**
     * Get presence status for a specific Duo ID
     */
    suspend fun getPresenceStatus(duoId: String): PresenceStatus? = suspendCancellableCoroutine { cont ->
        presenceRef.child(duoId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                    val deviceName = snapshot.child("deviceName").getValue(String::class.java) ?: ""
                    cont.resume(PresenceStatus(online, lastSeen, deviceName))
                } else {
                    cont.resume(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error getting presence", error.toException())
                cont.resume(null)
            }
        })
    }

    /**
     * Observe presence status for a specific Duo ID in real-time
     */
    fun observePresence(duoId: String): Flow<PresenceStatus?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                    val deviceName = snapshot.child("deviceName").getValue(String::class.java) ?: ""
                    trySend(PresenceStatus(online, lastSeen, deviceName))
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error observing presence", error.toException())
                trySend(null)
            }
        }

        presenceRef.child(duoId).addValueEventListener(listener)

        awaitClose {
            presenceRef.child(duoId).removeEventListener(listener)
        }
    }

    /**
     * Format last seen time as human-readable string
     */
    fun formatLastSeen(lastSeenTimestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - lastSeenTimestamp
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000} min ago"
            diff < 86400_000 -> "${diff / 3600_000} hours ago"
            diff < 604800_000 -> "${diff / 86400_000} days ago"
            else -> "Long time ago"
        }
    }
}
