package com.android.music.equalizer.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.android.music.equalizer.data.model.EqualizerPreset
import com.android.music.equalizer.data.model.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for persisting equalizer settings
 */
class EqualizerRepository(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "equalizer_prefs"
        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_PRESET_ID = "eq_preset_id"
        private const val KEY_BAND_LEVELS = "eq_band_levels"
        private const val KEY_BASS_BOOST = "eq_bass_boost"
        private const val KEY_VIRTUALIZER = "eq_virtualizer"

        @Volatile
        private var instance: EqualizerRepository? = null
        
        fun getInstance(context: Context): EqualizerRepository {
            return instance ?: synchronized(this) {
                instance ?: EqualizerRepository(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()
    
    private val _presets = MutableStateFlow(loadPresets())
    val presets: StateFlow<List<EqualizerPreset>> = _presets.asStateFlow()
    
    /**
     * Load saved equalizer state
     */
    private fun loadState(): EqualizerState {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val presetId = prefs.getString(KEY_PRESET_ID, EqualizerPreset.FLAT.id) ?: EqualizerPreset.FLAT.id
        val bandLevelsStr = prefs.getString(KEY_BAND_LEVELS, "0,0,0,0,0") ?: "0,0,0,0,0"
        val bandLevels = bandLevelsStr.split(",").mapNotNull { it.toIntOrNull() }
        val bassBoost = prefs.getInt(KEY_BASS_BOOST, 0)
        val virtualizer = prefs.getInt(KEY_VIRTUALIZER, 0)
        
        return EqualizerState(
            isEnabled = enabled,
            currentPresetId = presetId,
            bandLevels = bandLevels.ifEmpty { listOf(0, 0, 0, 0, 0) },
            bassBoost = bassBoost,
            virtualizer = virtualizer
        )
    }
    
    /**
     * Load all presets (default + custom)
     */
    private fun loadPresets(): List<EqualizerPreset> {
        val defaultPresets = EqualizerPreset.getDefaultPresets()
        // TODO: Load custom presets from storage
        return defaultPresets
    }
    
    /**
     * Save equalizer enabled state
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(isEnabled = enabled)
    }
    
    /**
     * Save current preset
     */
    fun setCurrentPreset(preset: EqualizerPreset) {
        prefs.edit()
            .putString(KEY_PRESET_ID, preset.id)
            .putString(KEY_BAND_LEVELS, preset.bandLevels.joinToString(","))
            .putInt(KEY_BASS_BOOST, preset.bassBoost)
            .putInt(KEY_VIRTUALIZER, preset.virtualizer)
            .apply()
        
        _state.value = _state.value.copy(
            currentPresetId = preset.id,
            bandLevels = preset.bandLevels,
            bassBoost = preset.bassBoost,
            virtualizer = preset.virtualizer
        )
    }
    
    /**
     * Save band levels
     */
    fun setBandLevels(levels: List<Int>) {
        prefs.edit()
            .putString(KEY_BAND_LEVELS, levels.joinToString(","))
            .putString(KEY_PRESET_ID, "custom")
            .apply()
        
        _state.value = _state.value.copy(
            bandLevels = levels,
            currentPresetId = "custom"
        )
    }
    
    /**
     * Save bass boost level
     */
    fun setBassBoost(strength: Int) {
        prefs.edit().putInt(KEY_BASS_BOOST, strength).apply()
        _state.value = _state.value.copy(bassBoost = strength)
    }
    
    /**
     * Save virtualizer level
     */
    fun setVirtualizer(strength: Int) {
        prefs.edit().putInt(KEY_VIRTUALIZER, strength).apply()
        _state.value = _state.value.copy(virtualizer = strength)
    }
    
    /**
     * Reset to flat preset
     */
    fun reset() {
        setCurrentPreset(EqualizerPreset.FLAT)
    }
}
