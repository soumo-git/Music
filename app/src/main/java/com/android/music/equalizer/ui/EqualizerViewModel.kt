package com.android.music.equalizer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.music.equalizer.audio.EqualizerEngine
import com.android.music.equalizer.data.model.EqualizerPreset
import com.android.music.equalizer.data.repository.EqualizerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Equalizer UI
 * Manages state and coordinates between UI and audio engine
 */
class EqualizerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = EqualizerRepository.getInstance(application)
    private val engine = EqualizerEngine.getInstance()
    
    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()
    
    val presets: StateFlow<List<EqualizerPreset>> = repository.presets
    
    init {
        viewModelScope.launch {
            // Observe repository state changes
            repository.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isEnabled = state.isEnabled,
                    currentPresetId = state.currentPresetId,
                    bandLevels = state.bandLevels,
                    bassBoost = state.bassBoost,
                    virtualizer = state.virtualizer
                )
            }
        }
    }
    
    /**
     * Initialize the equalizer with audio session
     */
    fun initialize(audioSessionId: Int) {
        // If engine is already initialized (by MusicService), just load the state
        if (engine.isInitialized() || audioSessionId == 0) {
            loadEngineState()
            return
        }
        
        val success = engine.initialize(audioSessionId)
        
        if (success) {
            loadEngineState()
        }
    }
    
    private fun loadEngineState() {
        // Apply saved settings
        val state = repository.state.value
        engine.setEnabled(state.isEnabled)
        engine.setAllBandLevels(state.bandLevels)
        engine.setBassBoostStrength(state.bassBoost)
        engine.setVirtualizerStrength(state.virtualizer)
        
        _uiState.value = _uiState.value.copy(
            isInitialized = true,
            numberOfBands = engine.getNumberOfBands(),
            minBandLevel = engine.getMinBandLevel(),
            maxBandLevel = engine.getMaxBandLevel(),
            bandFrequencies = getBandFrequencies(),
            isBassBoostSupported = engine.isBassBoostSupported(),
            isVirtualizerSupported = engine.isVirtualizerSupported()
        )
    }
    
    /**
     * Toggle equalizer on/off
     */
    fun setEnabled(enabled: Boolean) {
        engine.setEnabled(enabled)
        repository.setEnabled(enabled)
    }
    
    /**
     * Apply a preset
     */
    fun applyPreset(preset: EqualizerPreset) {
        // Adjust band levels to match device's band count
        val adjustedLevels = adjustBandLevels(preset.bandLevels)
        
        engine.setAllBandLevels(adjustedLevels)
        engine.setBassBoostStrength(preset.bassBoost)
        engine.setVirtualizerStrength(preset.virtualizer)
        
        repository.setCurrentPreset(preset.copy(bandLevels = adjustedLevels))
    }
    
    /**
     * Set a specific band level
     */
    fun setBandLevel(band: Int, level: Int) {
        engine.setBandLevel(band, level)
        
        val newLevels = _uiState.value.bandLevels.toMutableList()
        if (band < newLevels.size) {
            newLevels[band] = level
            repository.setBandLevels(newLevels)
        }
    }
    
    /**
     * Set bass boost strength
     */
    fun setBassBoost(strength: Int) {
        engine.setBassBoostStrength(strength)
        repository.setBassBoost(strength)
    }
    
    /**
     * Set virtualizer strength
     */
    fun setVirtualizer(strength: Int) {
        engine.setVirtualizerStrength(strength)
        repository.setVirtualizer(strength)
    }
    
    /**
     * Reset to flat
     */
    fun reset() {
        applyPreset(EqualizerPreset.FLAT)
    }
    
    /**
     * Get formatted frequency labels for bands
     */
    private fun getBandFrequencies(): List<String> {
        val bands = engine.getNumberOfBands()
        return (0 until bands).map { band ->
            val freq = engine.getCenterFrequency(band) / 1000 // Convert to Hz
            formatFrequency(freq)
        }
    }
    
    /**
     * Format frequency for display
     */
    private fun formatFrequency(hz: Int): String {
        return when {
            hz >= 1000 -> "${hz / 1000}kHz"
            else -> "${hz}Hz"
        }
    }
    
    /**
     * Adjust preset band levels to match device's band count
     */
    private fun adjustBandLevels(levels: List<Int>): List<Int> {
        val deviceBands = engine.getNumberOfBands()
        
        return when {
            levels.size == deviceBands -> levels
            levels.size > deviceBands -> levels.take(deviceBands)
            else -> {
                // Interpolate to fill missing bands
                val result = mutableListOf<Int>()
                for (i in 0 until deviceBands) {
                    val ratio = i.toFloat() / (deviceBands - 1)
                    val srcIndex = (ratio * (levels.size - 1)).toInt()
                    result.add(levels.getOrElse(srcIndex) { 0 })
                }
                result
            }
        }
    }

}

/**
 * UI State for the Equalizer screen
 */
data class EqualizerUiState(
    val isInitialized: Boolean = false,
    val isEnabled: Boolean = false,
    val currentPresetId: String = "flat",
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val numberOfBands: Int = 5,
    val minBandLevel: Int = -1500,
    val maxBandLevel: Int = 1500,
    val bandFrequencies: List<String> = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz"),
    val isBassBoostSupported: Boolean = true,
    val isVirtualizerSupported: Boolean = true
)
