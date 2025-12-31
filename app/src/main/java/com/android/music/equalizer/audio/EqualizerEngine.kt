package com.android.music.equalizer.audio

import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core audio processing engine for equalizer effects.
 * Wraps Android's AudioEffect APIs with a clean interface.
 * 
 * This class handles all audio DSP operations on a separate thread
 * to avoid blocking the UI thread.
 */
class EqualizerEngine {
    
    companion object {
        private const val TAG = "EqualizerEngine"
        private const val PRIORITY = 1000
        
        @Volatile
        private var instance: EqualizerEngine? = null
        
        fun getInstance(): EqualizerEngine {
            return instance ?: synchronized(this) {
                instance ?: EqualizerEngine().also { instance = it }
            }
        }
    }
    
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    
    private var audioSessionId: Int = 0
    private var isInitialized = false
    
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _bandLevels = MutableStateFlow<List<Int>>(emptyList())
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()
    
    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()
    
    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()
    
    /**
     * Initialize the equalizer engine with an audio session ID
     * If already initialized with a different session, release and re-initialize
     */
    fun initialize(sessionId: Int): Boolean {
        if (sessionId == 0) {
            Log.w(TAG, "Invalid audio session ID")
            return false
        }
        
        // If already initialized with the same session, just return success
        if (isInitialized && audioSessionId == sessionId) {
            Log.d(TAG, "Already initialized with session $sessionId")
            return true
        }
        
        try {
            // Release existing effects if any
            if (isInitialized) {
                Log.d(TAG, "Re-initializing with new session: $sessionId (old: $audioSessionId)")
                releaseEffects()
            }
            
            audioSessionId = sessionId
            
            // Initialize Equalizer
            equalizer = Equalizer(PRIORITY, sessionId).apply {
                enabled = _isEnabled.value
            }
            
            // Initialize Bass Boost
            bassBoost = try {
                BassBoost(PRIORITY, sessionId).apply {
                    enabled = _isEnabled.value
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bass boost not supported: ${e.message}")
                null
            }
            
            // Initialize Virtualizer
            virtualizer = try {
                Virtualizer(PRIORITY, sessionId).apply {
                    enabled = _isEnabled.value
                }
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer not supported: ${e.message}")
                null
            }
            
            // Initialize band levels
            equalizer?.let { eq ->
                val levels = (0 until eq.numberOfBands).map { 
                    eq.getBandLevel(it.toShort()).toInt() 
                }
                _bandLevels.value = levels
            }
            
            isInitialized = true
            Log.d(TAG, "Equalizer engine initialized with session $sessionId")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize equalizer: ${e.message}")
            releaseEffects()
            return false
        }
    }
    
    /**
     * Release only the audio effects, keep the state
     */
    private fun releaseEffects() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing effects: ${e.message}")
        } finally {
            equalizer = null
            bassBoost = null
            virtualizer = null
        }
    }

    
    /**
     * Enable or disable the equalizer
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        Log.d(TAG, "Equalizer enabled: $enabled")
    }
    
    /**
     * Get the number of frequency bands
     */
    fun getNumberOfBands(): Int {
        return equalizer?.numberOfBands?.toInt() ?: 5
    }
    
    /**
     * Get the frequency range for a band
     */
    fun getBandFrequencyRange(band: Int): IntArray {
        return equalizer?.getBandFreqRange(band.toShort()) ?: intArrayOf(0, 0)
    }
    
    /**
     * Get the center frequency for a band in Hz
     */
    fun getCenterFrequency(band: Int): Int {
        return equalizer?.getCenterFreq(band.toShort()) ?: 0
    }
    
    /**
     * Get the minimum band level in millibels
     */
    fun getMinBandLevel(): Int {
        return equalizer?.bandLevelRange?.get(0)?.toInt() ?: -1500
    }
    
    /**
     * Get the maximum band level in millibels
     */
    fun getMaxBandLevel(): Int {
        return equalizer?.bandLevelRange?.get(1)?.toInt() ?: 1500
    }
    
    /**
     * Set the level for a specific band
     * @param band Band index (0 to numberOfBands-1)
     * @param level Level in millibels
     */
    fun setBandLevel(band: Int, level: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), level.toShort())
            
            // Update state
            val currentLevels = _bandLevels.value.toMutableList()
            if (band < currentLevels.size) {
                currentLevels[band] = level
                _bandLevels.value = currentLevels
            }
            
            Log.d(TAG, "Band $band level set to $level")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set band level: ${e.message}")
        }
    }
    
    /**
     * Set all band levels at once
     */
    fun setAllBandLevels(levels: List<Int>) {
        levels.forEachIndexed { index, level ->
            try {
                equalizer?.setBandLevel(index.toShort(), level.toShort())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set band $index: ${e.message}")
            }
        }
        _bandLevels.value = levels
    }
    
    /**
     * Set bass boost strength (0-1000)
     */
    fun setBassBoostStrength(strength: Int) {
        try {
            bassBoost?.setStrength(strength.toShort())
            _bassBoostStrength.value = strength
            Log.d(TAG, "Bass boost strength set to $strength")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set bass boost: ${e.message}")
        }
    }
    
    /**
     * Set virtualizer strength (0-1000)
     */
    fun setVirtualizerStrength(strength: Int) {
        try {
            virtualizer?.setStrength(strength.toShort())
            _virtualizerStrength.value = strength
            Log.d(TAG, "Virtualizer strength set to $strength")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set virtualizer: ${e.message}")
        }
    }
    
    /**
     * Check if bass boost is supported
     */
    fun isBassBoostSupported(): Boolean = bassBoost != null
    
    /**
     * Check if virtualizer is supported
     */
    fun isVirtualizerSupported(): Boolean = virtualizer != null
    
    /**
     * Release all audio effects
     */
    fun release() {
        releaseEffects()
        isInitialized = false
        audioSessionId = 0
    }
    
    /**
     * Check if the engine is initialized
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Get current audio session ID
     */
    fun getAudioSessionId(): Int = audioSessionId
}
