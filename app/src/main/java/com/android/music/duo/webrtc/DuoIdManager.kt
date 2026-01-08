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
 * Duo ID is now tied to Google account - no ID without login
 */
class DuoIdManager(private val context: Context) {

    companion object {
        private const val TAG = "DuoIdManager"
        private const val PREFS_NAME = "duo_prefs"
        private const val KEY_DUO_ID = "duo_id"
        const val ID_LENGTH = 12
        
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
     * Check if user is signed in
     */
    fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Get or create Duo ID for the current user
     * Returns null if user is not signed in
     */
    suspend fun getOrCreateDuoId(): String? {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "User not signed in, cannot get/create Duo ID")
            return null
        }

        // First check if user already has an ID in Firebase (linked to their Google account)
        val firebaseId = getFirebaseDuoIdByGoogleUid(currentUser.uid)
        if (firebaseId != null) {
            Log.d(TAG, "Found existing Firebase Duo ID: $firebaseId")
            saveLocalDuoId(firebaseId)
            // Update device name
            syncWithFirebase(firebaseId)
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
     * Get locally stored Duo ID (cache)
     */
    fun getLocalDuoId(): String? {
        return prefs.getString(KEY_DUO_ID, null)
    }

    /**
     * Save Duo ID locally (cache)
     */
    private fun saveLocalDuoId(duoId: String) {
        prefs.edit().putString(KEY_DUO_ID, duoId).apply()
    }

    /**
     * Clear local Duo ID (on sign out)
     */
    fun clearLocalDuoId() {
        prefs.edit().remove(KEY_DUO_ID).apply()
    }

    /**
     * Get Duo ID from Firebase by Google UID
     */
    private suspend fun getFirebaseDuoIdByGoogleUid(googleUid: String): String? = suspendCancellableCoroutine { cont ->
        usersRef.orderByChild("googleUid").equalTo(googleUid)
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
     * Get Duo ID from Firebase for current user
     */
    suspend fun getCurrentUserDuoId(): String? {
        val currentUser = auth.currentUser ?: return null
        return getFirebaseDuoIdByGoogleUid(currentUser.uid)
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
    suspend fun idExistsInFirebase(duoId: String): Boolean = suspendCancellableCoroutine { cont ->
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
     * Save Duo ID to Firebase (linked to Google account)
     */
    private fun saveToFirebase(duoId: String) {
        val currentUser = auth.currentUser ?: return
        val duoUser = DuoUser(
            duoId = duoId,
            googleUid = currentUser.uid,
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
     * Sync local ID with Firebase (update device name)
     */
    private fun syncWithFirebase(duoId: String) {
        val updates = mutableMapOf<String, Any>(
            "deviceName" to Build.MODEL
        )

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

    /**
     * Change Duo ID to a custom one
     * Returns true if successful, false if ID already exists or invalid
     */
    suspend fun changeDuoId(newDuoId: String): Result<String> {
        val currentUser = auth.currentUser
            ?: return Result.failure(Exception("User not signed in"))

        // Validate format
        if (newDuoId.length != ID_LENGTH || !newDuoId.all { it.isDigit() }) {
            return Result.failure(Exception("ID must be exactly $ID_LENGTH digits"))
        }

        // Check if ID already exists
        if (idExistsInFirebase(newDuoId)) {
            return Result.failure(Exception("This ID is already taken"))
        }

        // Get current Duo ID
        val currentDuoId = getFirebaseDuoIdByGoogleUid(currentUser.uid)

        return suspendCancellableCoroutine { cont ->
            // Create new entry
            val duoUser = DuoUser(
                duoId = newDuoId,
                googleUid = currentUser.uid,
                deviceName = Build.MODEL,
                createdAt = System.currentTimeMillis()
            )

            usersRef.child(newDuoId).setValue(duoUser)
                .addOnSuccessListener {
                    // Delete old entry if exists
                    currentDuoId?.let { oldId ->
                        usersRef.child(oldId).removeValue()
                    }
                    saveLocalDuoId(newDuoId)
                    Log.d(TAG, "Duo ID changed to: $newDuoId")
                    cont.resume(Result.success(newDuoId))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to change Duo ID", e)
                    cont.resume(Result.failure(e))
                }
        }
    }

    /**
     * Generate and set a new random Duo ID
     */
    suspend fun regenerateDuoId(): Result<String> {
        val currentUser = auth.currentUser
            ?: return Result.failure(Exception("User not signed in"))

        val newId = generateUniqueDuoId()
        return changeDuoId(newId)
    }
}
