package com.android.music.equalizer.manager

import android.content.Context
import android.util.Log
import com.android.music.equalizer.audio.EqualizerEngine
import com.android.music.equalizer.data.repository.EqualizerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Manager that automatically applies equalizer settings to new audio sessions
 * This ensures the equalizer works consistently across song changes
 */
class EqualizerManager private constructor(context: Context) {
    
    companion object {
        private const val TAG = "EqualizerManager"
        
        @Volatile
        private var instance: EqualizerManager? = null
        
        fun getInstance(context: Context): EqualizerManager {
            return instance ?: synchronized(this) {
                instance ?: EqualizerManager(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
    
    private val repository = EqualizerRepository.getInstance(context)
    private val engine = EqualizerEngine.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var isObservingSettings = false
    
    init {
        startObservingSettings()
    }
    
    /**
     * Initialize equalizer with a new audio session and apply saved settings
     */
    fun initializeWithAudioSession(audioSessionId: Int) {
        Log.d(TAG, "Initializing equalizer with audio session: $audioSessionId")
        
        val success = engine.initialize(audioSessionId)
        if (success) {
            applySavedSettings()
            Log.d(TAG, "Equalizer initialized and settings applied")
        } else {
            Log.w(TAG, "Failed to initialize equalizer with session: $audioSessionId")
        }
    }
    
    /**
     * Apply saved settings to the current engine
     */
    private fun applySavedSettings() {
        val state = repository.state.value
        
        try {
            engine.setEnabled(state.isEnabled)
            engine.setAllBandLevels(state.bandLevels)
            engine.setBassBoostStrength(state.bassBoost)
            engine.setVirtualizerStrength(state.virtualizer)
            
            Log.d(TAG, "Applied settings - Enabled: ${state.isEnabled}, " +
                    "Bands: ${state.bandLevels}, Bass: ${state.bassBoost}, " +
                    "Virtualizer: ${state.virtualizer}")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying settings: ${e.message}")
        }
    }
    
    /**
     * Start observing settings changes and apply them automatically
     */
    private fun startObservingSettings() {
        if (isObservingSettings) return
        
        isObservingSettings = true
        scope.launch {
            repository.state.collectLatest { state ->
                if (engine.isInitialized()) {
                    try {
                        engine.setEnabled(state.isEnabled)
                        engine.setAllBandLevels(state.bandLevels)
                        engine.setBassBoostStrength(state.bassBoost)
                        engine.setVirtualizerStrength(state.virtualizer)
                        
                        Log.d(TAG, "Auto-applied settings change")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error auto-applying settings: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * Check if equalizer is currently enabled
     */
    fun isEnabled(): Boolean {
        return repository.state.value.isEnabled
    }
    
    /**
     * Get current equalizer state
     */
    fun getCurrentState() = repository.state.value
}