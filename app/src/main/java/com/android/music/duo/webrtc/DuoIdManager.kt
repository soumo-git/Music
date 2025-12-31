package com.android.music.duo.webrtc

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.android.music.config.FirebaseConfig
import com.android.music.duo.webrtc.model.DuoUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * Manages Duo ID generation, storage, and Firebase synchronization
 */
class DuoIdManager(private val context: Context) {

    companion object {
        private const val TAG = "DuoIdManager"
        private const val PREFS_NAME = "duo_prefs"
        private const val KEY_DUO_ID = "duo_id"
        private const val ID_LENGTH = 12
        
        @Volatile
        private var instance: DuoIdManager? = null
        
        fun getInstance(context: Context): DuoIdManager {
            return instance ?: synchronized(this) {
                instance ?: DuoIdManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = FirebaseDatabase.getInstance(FirebaseConfig.RTDB_URL)
    private val auth = FirebaseAuth.getInstance()
    private val usersRef = database.getReference("users")

    /**
     * Get or create Duo ID for the current user
     */
    suspend fun getOrCreateDuoId(): String {
        // First check local storage
        val localId = getLocalDuoId()
        if (localId != null) {
            Log.d(TAG, "Found local Duo ID: $localId")
            // Verify it exists in Firebase and sync
            syncWithFirebase(localId)
            return localId
        }

        // Check if user is signed in and has an ID in Firebase
        val firebaseId = getFirebaseDuoId()
        if (firebaseId != null) {
            Log.d(TAG, "Found Firebase Duo ID: $firebaseId")
            saveLocalDuoId(firebaseId)
            return firebaseId
        }

        // Generate new unique ID
        val newId = generateUniqueDuoId()
        Log.d(TAG, "Generated new Duo ID: $newId")
        saveLocalDuoId(newId)
        saveToFirebase(newId)
        return newId
    }

    /**
     * Get locally stored Duo ID
     */
    fun getLocalDuoId(): String? {
        return prefs.getString(KEY_DUO_ID, null)
    }

    /**
     * Save Duo ID locally
     */
    private fun saveLocalDuoId(duoId: String) {
        prefs.edit().putString(KEY_DUO_ID, duoId).apply()
    }

    /**
     * Get Duo ID from Firebase (if user is signed in)
     */
    private suspend fun getFirebaseDuoId(): String? = suspendCancellableCoroutine { cont ->
        val currentUser = auth.currentUser
        if (currentUser == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        usersRef.orderByChild("googleUid").equalTo(currentUser.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val duoId = snapshot.children.firstOrNull()?.key
                        cont.resume(duoId)
                    } else {
                        cont.resume(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error getting Firebase Duo ID", error.toException())
                    cont.resume(null)
                }
            })
    }

    /**
     * Generate a unique 12-digit Duo ID
     */
    private suspend fun generateUniqueDuoId(): String {
        var attempts = 0
        val maxAttempts = 100

        while (attempts < maxAttempts) {
            val candidateId = generateRandomId()
            if (!idExistsInFirebase(candidateId)) {
                return candidateId
            }
            attempts++
            Log.d(TAG, "ID collision, attempt $attempts")
        }

        // Fallback: use timestamp-based ID
        return System.currentTimeMillis().toString().takeLast(ID_LENGTH).padStart(ID_LENGTH, '0')
    }

    /**
     * Generate a random 12-digit number
     */
    private fun generateRandomId(): String {
        val sb = StringBuilder()
        repeat(ID_LENGTH) {
            sb.append(Random.nextInt(0, 10))
        }
        return sb.toString()
    }

    /**
     * Check if ID exists in Firebase
     */
    private suspend fun idExistsInFirebase(duoId: String): Boolean = suspendCancellableCoroutine { cont ->
        usersRef.child(duoId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cont.resume(snapshot.exists())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error checking ID existence", error.toException())
                cont.resume(false)
            }
        })
    }

    /**
     * Save Duo ID to Firebase
     */
    private fun saveToFirebase(duoId: String) {
        val currentUser = auth.currentUser
        val duoUser = DuoUser(
            duoId = duoId,
            googleUid = currentUser?.uid,
            deviceName = Build.MODEL,
            createdAt = System.currentTimeMillis()
        )

        usersRef.child(duoId).setValue(duoUser)
            .addOnSuccessListener {
                Log.d(TAG, "Duo ID saved to Firebase")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save Duo ID to Firebase", e)
            }
    }

    /**
     * Sync local ID with Firebase (update device name, link Google account)
     */
    private fun syncWithFirebase(duoId: String) {
        val currentUser = auth.currentUser
        val updates = mutableMapOf<String, Any>(
            "deviceName" to Build.MODEL
        )
        
        // Link Google account if signed in
        currentUser?.uid?.let {
            updates["googleUid"] = it
        }

        usersRef.child(duoId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Synced with Firebase")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync with Firebase", e)
            }
    }

    /**
     * Validate if a Duo ID exists
     */
    suspend fun validateDuoId(duoId: String): Boolean {
        if (duoId.length != ID_LENGTH || !duoId.all { it.isDigit() }) {
            return false
        }
        return idExistsInFirebase(duoId)
    }
}
