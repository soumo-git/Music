package com.android.music.videoplayer.engine.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Configuration for the video player engine.
 * Stores engine preferences and settings.
 */
class VideoEngineConfig(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    /**
     * Currently installed engine version
     */
    var installedVersion: String?
        get() = prefs.getString(KEY_INSTALLED_VERSION, null)
        set(value) = prefs.edit { putString(KEY_INSTALLED_VERSION, value) }
    
    /**
     * Last known latest version from remote
     */
    var latestVersion: String?
        get() = prefs.getString(KEY_LATEST_VERSION, null)
        set(value) = prefs.edit { putString(KEY_LATEST_VERSION, value) }
    
    /**
     * Timestamp of last version check
     */
    var lastVersionCheck: Long
        get() = prefs.getLong(KEY_LAST_VERSION_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_VERSION_CHECK, value) }
    
    /**
     * Whether to auto-check for updates
     */
    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CHECK_UPDATES, value) }
    
    /**
     * Preferred video quality (auto, 1080p, 720p, etc.)
     */
    var preferredVideoQuality: String
        get() = prefs.getString(KEY_PREFERRED_VIDEO_QUALITY, DEFAULT_VIDEO_QUALITY) ?: DEFAULT_VIDEO_QUALITY
        set(value) = prefs.edit { putString(KEY_PREFERRED_VIDEO_QUALITY, value) }
    
    /**
     * Preferred playback speed (0.5x, 1.0x, 1.25x, 1.5x, 2.0x)
     */
    var preferredPlaybackSpeed: Float
        get() = prefs.getFloat(KEY_PREFERRED_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED)
        set(value) = prefs.edit { putFloat(KEY_PREFERRED_PLAYBACK_SPEED, value.coerceIn(0.25f, 3f)) }
    
    /**
     * Default volume level (0.0 to 1.0)
     */
    var defaultVolume: Float
        get() = prefs.getFloat(KEY_DEFAULT_VOLUME, DEFAULT_VOLUME)
        set(value) = prefs.edit { putFloat(KEY_DEFAULT_VOLUME, value.coerceIn(0f, 1f)) }
    
    /**
     * Whether to auto-play videos when loaded
     */
    var autoPlay: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_PLAY, value) }
    
    /**
     * Whether to show video controls by default
     */
    var showControls: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CONTROLS, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_CONTROLS, value) }
    
    /**
     * Controls auto-hide timeout in milliseconds
     */
    var controlsAutoHideTimeout: Long
        get() = prefs.getLong(KEY_CONTROLS_AUTO_HIDE_TIMEOUT, DEFAULT_CONTROLS_TIMEOUT)
        set(value) = prefs.edit { putLong(KEY_CONTROLS_AUTO_HIDE_TIMEOUT, value.coerceAtLeast(1000L)) }
    
    /**
     * Whether to use hardware acceleration
     */
    var useHardwareAcceleration: Boolean
        get() = prefs.getBoolean(KEY_USE_HARDWARE_ACCELERATION, true)
        set(value) = prefs.edit { putBoolean(KEY_USE_HARDWARE_ACCELERATION, value) }
    
    /**
     * Buffer size in seconds
     */
    var bufferSize: Int
        get() = prefs.getInt(KEY_BUFFER_SIZE, DEFAULT_BUFFER_SIZE)
        set(value) = prefs.edit { putInt(KEY_BUFFER_SIZE, value.coerceIn(5, 60)) }
    
    /**
     * Selected video engine name
     */
    var selectedEngine: String
        get() = prefs.getString(KEY_SELECTED_ENGINE, DEFAULT_ENGINE) ?: DEFAULT_ENGINE
        set(value) = prefs.edit { putString(KEY_SELECTED_ENGINE, value) }
    
    /**
     * Check if version cache is still valid
     */
    fun isVersionCacheValid(): Boolean {
        val elapsed = System.currentTimeMillis() - lastVersionCheck
        return elapsed < VERSION_CACHE_DURATION_MS
    }
    
    /**
     * Clear all engine configuration
     */
    fun clear() {
        prefs.edit { clear() }
    }
    
    companion object {
        private const val PREFS_NAME = "video_engine_config"
        
        private const val KEY_INSTALLED_VERSION = "installed_version"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_LAST_VERSION_CHECK = "last_version_check"
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        private const val KEY_PREFERRED_VIDEO_QUALITY = "preferred_video_quality"
        private const val KEY_PREFERRED_PLAYBACK_SPEED = "preferred_playback_speed"
        private const val KEY_DEFAULT_VOLUME = "default_volume"
        private const val KEY_AUTO_PLAY = "auto_play"
        private const val KEY_SHOW_CONTROLS = "show_controls"
        private const val KEY_CONTROLS_AUTO_HIDE_TIMEOUT = "controls_auto_hide_timeout"
        private const val KEY_USE_HARDWARE_ACCELERATION = "use_hardware_acceleration"
        private const val KEY_BUFFER_SIZE = "buffer_size"
        private const val KEY_SELECTED_ENGINE = "selected_engine"
        
        private const val DEFAULT_VIDEO_QUALITY = "auto"
        private const val DEFAULT_PLAYBACK_SPEED = 1.0f
        private const val DEFAULT_VOLUME = 1.0f
        private const val DEFAULT_CONTROLS_TIMEOUT = 3000L // 3 seconds
        private const val DEFAULT_BUFFER_SIZE = 15 // 15 seconds
        private const val DEFAULT_ENGINE = "ExoPlayer"
        
        // Cache version check for 6 hours
        private const val VERSION_CACHE_DURATION_MS = 6 * 60 * 60 * 1000L
    }
}