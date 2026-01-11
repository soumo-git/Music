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
     * Timestamp of last version check
     */
    var lastVersionCheck: Long
        get() = prefs.getLong(KEY_LAST_VERSION_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_VERSION_CHECK, value) }

    /**
     * Clear all engine configuration
     */
    fun clear() {
        prefs.edit { clear() }
    }
    
    companion object {
        private const val PREFS_NAME = "download_engine_config"
        
        private const val KEY_INSTALLED_VERSION = "installed_version"
        private const val KEY_LAST_VERSION_CHECK = "last_version_check"

    }
}
