package com.android.music.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages play count tracking for songs using SharedPreferences
 */
class PlayCountManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "play_count_prefs"
        private const val KEY_PREFIX = "play_count_"

        @Volatile
        private var instance: PlayCountManager? = null

        fun getInstance(context: Context): PlayCountManager {
            return instance ?: synchronized(this) {
                instance ?: PlayCountManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get play count for a song
     */
    fun getPlayCount(songId: Long): Int {
        return prefs.getInt("$KEY_PREFIX$songId", 0)
    }

    /**
     * Increment play count for a song
     */
    fun incrementPlayCount(songId: Long) {
        val currentCount = getPlayCount(songId)
        prefs.edit().putInt("$KEY_PREFIX$songId", currentCount + 1).apply()
    }

    /**
     * Get all play counts as a map
     */
    fun getAllPlayCounts(): Map<Long, Int> {
        val result = mutableMapOf<Long, Int>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is Int) {
                val songId = key.removePrefix(KEY_PREFIX).toLongOrNull()
                songId?.let { result[it] = value }
            }
        }
        return result
    }

    /**
     * Reset play count for a song
     */
    fun resetPlayCount(songId: Long) {
        prefs.edit().remove("$KEY_PREFIX$songId").apply()
    }
}
