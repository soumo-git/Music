package com.android.music.download.engine.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Configuration for the download engine.
 * Stores engine preferences and settings.
 */
class EngineConfig(context: Context) {
    
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
     * Preferred audio format (mp3, m4a, opus, etc.)
     */
    var preferredAudioFormat: String
        get() = prefs.getString(KEY_PREFERRED_AUDIO_FORMAT, DEFAULT_AUDIO_FORMAT) ?: DEFAULT_AUDIO_FORMAT
        set(value) = prefs.edit { putString(KEY_PREFERRED_AUDIO_FORMAT, value) }
    
    /**
     * Preferred video quality (best, 1080p, 720p, etc.)
     */
    var preferredVideoQuality: String
        get() = prefs.getString(KEY_PREFERRED_VIDEO_QUALITY, DEFAULT_VIDEO_QUALITY) ?: DEFAULT_VIDEO_QUALITY
        set(value) = prefs.edit { putString(KEY_PREFERRED_VIDEO_QUALITY, value) }
    
    /**
     * Download directory path
     */
    var downloadDirectory: String?
        get() = prefs.getString(KEY_DOWNLOAD_DIRECTORY, null)
        set(value) = prefs.edit { putString(KEY_DOWNLOAD_DIRECTORY, value) }
    
    /**
     * Whether to embed thumbnail in downloaded files
     */
    var embedThumbnail: Boolean
        get() = prefs.getBoolean(KEY_EMBED_THUMBNAIL, true)
        set(value) = prefs.edit { putBoolean(KEY_EMBED_THUMBNAIL, value) }
    
    /**
     * Whether to embed metadata in downloaded files
     */
    var embedMetadata: Boolean
        get() = prefs.getBoolean(KEY_EMBED_METADATA, true)
        set(value) = prefs.edit { putBoolean(KEY_EMBED_METADATA, value) }
    
    /**
     * Maximum concurrent downloads
     */
    var maxConcurrentDownloads: Int
        get() = prefs.getInt(KEY_MAX_CONCURRENT_DOWNLOADS, DEFAULT_MAX_CONCURRENT)
        set(value) = prefs.edit { putInt(KEY_MAX_CONCURRENT_DOWNLOADS, value.coerceIn(1, 5)) }
    
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
        private const val PREFS_NAME = "download_engine_config"
        
        private const val KEY_INSTALLED_VERSION = "installed_version"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_LAST_VERSION_CHECK = "last_version_check"
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        private const val KEY_PREFERRED_AUDIO_FORMAT = "preferred_audio_format"
        private const val KEY_PREFERRED_VIDEO_QUALITY = "preferred_video_quality"
        private const val KEY_DOWNLOAD_DIRECTORY = "download_directory"
        private const val KEY_EMBED_THUMBNAIL = "embed_thumbnail"
        private const val KEY_EMBED_METADATA = "embed_metadata"
        private const val KEY_MAX_CONCURRENT_DOWNLOADS = "max_concurrent_downloads"
        
        private const val DEFAULT_AUDIO_FORMAT = "mp3"
        private const val DEFAULT_VIDEO_QUALITY = "best"
        private const val DEFAULT_MAX_CONCURRENT = 2
        
        // Cache version check for 6 hours
        private const val VERSION_CACHE_DURATION_MS = 6 * 60 * 60 * 1000L
    }
}
