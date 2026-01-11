package com.android.music.duo.webrtc

import android.util.Log
import com.android.music.config.FirebaseConfig
import com.android.music.duo.webrtc.model.NotifyRequest
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages WebRTC signaling through Firebase RTDB
 * 
 * Firebase structure:
 * /presence/{duoId} {
 *   online: true/false,
 *   lastSeen: timestamp,
 *   deviceName: "Device Name",
 *   offer: "sdp_offer_string",      // Written by caller
 *   offerFrom: "caller_duo_id",     // Who sent the offer
 *   offerFromDevice: "Device Name", // Caller's device name
 *   answer: "sdp_answer_string"     // Written by callee
 * }
 */
class SignalingManager {

    companion object {
        private const val TAG = "SignalingManager"
        private const val PRESENCE_PATH = "presence"
        private const val NOTIFY_PATH = "notify_requests"
        
        @Volatile
        private var instance: SignalingManager? = null
        
        fun getInstance(): SignalingManager {
            return instance ?: synchronized(this) {
                instance ?: SignalingManager().also { instance = it }
            }
        }
    }

    private val database = FirebaseDatabase.getInstance(FirebaseConfig.RTDB_URL)
    private val presenceRef = database.getReference(PRESENCE_PATH)
    private val notifyRef = database.getReference(NOTIFY_PATH)
    
    private var offerListener: ValueEventListener? = null
    private var answerListener: ValueEventListener? = null

    /**
     * Send SDP offer to target user's presence node
     */
    suspend fun sendOffer(
        fromId: String,
        toId: String,
        sdpOffer: String,
        fromDeviceName: String
    ): Boolean = suspendCancellableCoroutine { cont ->
        Log.d(TAG, "Sending offer to $toId, SDP length: ${sdpOffer.length}")
        
        val updates = mapOf(
            "offer" to sdpOffer,
            "offerFrom" to fromId,
            "offerFromDevice" to fromDeviceName,
            "offerTimestamp" to ServerValue.TIMESTAMP,
            "answer" to null // Clear any previous answer
        )

        presenceRef.child(toId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Offer sent successfully to $toId")
                cont.resume(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send offer", e)
                cont.resume(false)
            }
    }

    /**
     * Send SDP answer back to caller
     */
    suspend fun sendAnswer(
        fromId: String,
        toId: String,
        sdpAnswer: String
    ): Boolean = suspendCancellableCoroutine { cont ->
        Log.d(TAG, "Sending answer to $toId, SDP length: ${sdpAnswer.length}")
        
        // Write answer to caller's presence node
        val updates = mapOf(
            "answer" to sdpAnswer,
            "answerFrom" to fromId,
            "answerTimestamp" to ServerValue.TIMESTAMP
        )

        presenceRef.child(toId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Answer sent successfully to $toId")
                cont.resume(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send answer", e)
                cont.resume(false)
            }
    }

    /**
     * Observe incoming offers on my presence node
     */
    fun observeIncomingOffer(myDuoId: String): Flow<IncomingOffer?> = callbackFlow {
        Log.d(TAG, "Starting to observe incoming offers for $myDuoId")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val offer = snapshot.child("offer").getValue(String::class.java)
                val offerFrom = snapshot.child("offerFrom").getValue(String::class.java)
                val offerFromDevice = snapshot.child("offerFromDevice").getValue(String::class.java)
                
                if (offer != null && offerFrom != null && offerFrom != myDuoId) {
                    Log.d(TAG, "Received offer from $offerFrom (${offerFromDevice})")
                    trySend(IncomingOffer(
                        fromId = offerFrom,
                        fromDeviceName = offerFromDevice ?: "Unknown Device",
                        sdpOffer = offer
                    ))
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error observing offers", error.toException())
                trySend(null)
            }
        }

        offerListener = listener
        presenceRef.child(myDuoId).addValueEventListener(listener)

        awaitClose {
            Log.d(TAG, "Stopping offer observation for $myDuoId")
            presenceRef.child(myDuoId).removeEventListener(listener)
        }
    }

    /**
     * Observe answer on my presence node (after sending offer)
     */
    fun observeAnswer(myDuoId: String): Flow<String?> = callbackFlow {
        Log.d(TAG, "Starting to observe answer for $myDuoId")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val answer = snapshot.child("answer").getValue(String::class.java)
                val answerFrom = snapshot.child("answerFrom").getValue(String::class.java)
                
                if (answer != null && answerFrom != null) {
                    Log.d(TAG, "Received answer from $answerFrom, length: ${answer.length}")
                    trySend(answer)
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error observing answer", error.toException())
                trySend(null)
            }
        }

        answerListener = listener
        presenceRef.child(myDuoId).addValueEventListener(listener)

        awaitClose {
            Log.d(TAG, "Stopping answer observation for $myDuoId")
            presenceRef.child(myDuoId).removeEventListener(listener)
        }
    }

    /**
     * Clear offer/answer data after connection established or rejected
     */
    fun clearSignalingData(duoId: String) {
        Log.d(TAG, "Clearing signaling data for $duoId")
        val updates = mapOf(
            "offer" to null,
            "offerFrom" to null,
            "offerFromDevice" to null,
            "offerTimestamp" to null,
            "answer" to null,
            "answerFrom" to null,
            "answerTimestamp" to null
        )
        presenceRef.child(duoId).updateChildren(updates)
    }

    /**
     * Request notification when user comes online
     */
    suspend fun requestNotifyWhenOnline(
        requesterId: String,
        requesterDeviceName: String,
        targetId: String
    ): Boolean = suspendCancellableCoroutine { cont ->
        val request = NotifyRequest(
            requesterId = requesterId,
            requesterDeviceName = requesterDeviceName,
            timestamp = System.currentTimeMillis()
        )

        notifyRef.child(targetId).child(requesterId).setValue(request)
            .addOnSuccessListener {
                Log.d(TAG, "Notify request sent for $targetId")
                cont.resume(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send notify request", e)
                cont.resume(false)
            }
    }

    /**
     * Cleanup all listeners
     */
    fun cleanup() {
        offerListener = null
        answerListener = null
    }
    
    /**
     * Data class for incoming offer
     */
    data class IncomingOffer(
        val fromId: String,
        val fromDeviceName: String,
        val sdpOffer: String
    )
}
