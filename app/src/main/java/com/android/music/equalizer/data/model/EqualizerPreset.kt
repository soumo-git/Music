package com.android.music.equalizer.data.model

/**
 * Represents an equalizer preset with band levels and effects settings
 */
data class EqualizerPreset(
    val id: String,
    val name: String,
    val bandLevels: List<Int>,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val isCustom: Boolean = false
) {
    companion object {
        /**
         * Default presets for common audio profiles
         */
        val FLAT = EqualizerPreset(
            id = "flat",
            name = "Flat",
            bandLevels = listOf(0, 0, 0, 0, 0),
            bassBoost = 0,
            virtualizer = 0
        )
        
        val BASS_BOOST = EqualizerPreset(
            id = "bass_boost",
            name = "Bass Boost",
            bandLevels = listOf(600, 400, 0, 0, 0),
            bassBoost = 500,
            virtualizer = 0
        )
        
        val ROCK = EqualizerPreset(
            id = "rock",
            name = "Rock",
            bandLevels = listOf(400, 200, -100, 200, 400),
            bassBoost = 300,
            virtualizer = 200
        )
        
        val POP = EqualizerPreset(
            id = "pop",
            name = "Pop",
            bandLevels = listOf(-100, 200, 400, 200, -100),
            bassBoost = 200,
            virtualizer = 300
        )
        
        val JAZZ = EqualizerPreset(
            id = "jazz",
            name = "Jazz",
            bandLevels = listOf(300, 0, 100, 200, 300),
            bassBoost = 200,
            virtualizer = 400
        )
        
        val CLASSICAL = EqualizerPreset(
            id = "classical",
            name = "Classical",
            bandLevels = listOf(300, 200, -100, 200, 300),
            bassBoost = 0,
            virtualizer = 500
        )
        
        val ELECTRONIC = EqualizerPreset(
            id = "electronic",
            name = "Electronic",
            bandLevels = listOf(500, 300, 0, 200, 400),
            bassBoost = 600,
            virtualizer = 400
        )
        
        val HIP_HOP = EqualizerPreset(
            id = "hip_hop",
            name = "Hip Hop",
            bandLevels = listOf(500, 400, 0, 100, 300),
            bassBoost = 700,
            virtualizer = 300
        )
        
        val ACOUSTIC = EqualizerPreset(
            id = "acoustic",
            name = "Acoustic",
            bandLevels = listOf(300, 100, 100, 200, 200),
            bassBoost = 100,
            virtualizer = 200
        )
        
        val VOCAL = EqualizerPreset(
            id = "vocal",
            name = "Vocal",
            bandLevels = listOf(-200, 0, 300, 300, 100),
            bassBoost = 0,
            virtualizer = 400
        )
        
        /**
         * Get all default presets
         */
        fun getDefaultPresets(): List<EqualizerPreset> = listOf(
            FLAT, BASS_BOOST, ROCK, POP, JAZZ, 
            CLASSICAL, ELECTRONIC, HIP_HOP, ACOUSTIC, VOCAL
        )
    }
}

/**
 * Current equalizer state
 */
data class EqualizerState(
    val isEnabled: Boolean = false,
    val currentPresetId: String = EqualizerPreset.FLAT.id,
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val bassBoost: Int = 0,
    val virtualizer: Int = 0
)
