package com.android.music.videoplayer.engine.manager

import android.content.Context
import com.android.music.videoplayer.engine.config.VideoEngineConfig

/**
 * Factory for creating VideoEngineManager instances
 */
object VideoEngineManagerFactory {
    
    @Volatile
    private var instance: VideoEngineManager? = null
    
    fun getInstance(context: Context): VideoEngineManager {
        return instance ?: synchronized(this) {
            instance ?: createEngineManager(context).also { instance = it }
        }
    }
    
    private fun createEngineManager(context: Context): VideoEngineManager {
        val config = VideoEngineConfig(context)
        val selectedEngine = config.selectedEngine
        
        return when (selectedEngine) {
            "ExoPlayer" -> ExoPlayerEngineManager(context.applicationContext)
            // Future engines can be added here:
            // "VLC" -> VLCEngineManager(context.applicationContext)
            // "IJKPlayer" -> IJKPlayerEngineManager(context.applicationContext)
            else -> {
                // Default to ExoPlayer
                config.selectedEngine = "ExoPlayer"
                ExoPlayerEngineManager(context.applicationContext)
            }
        }
    }
    
    /**
     * Switch to a different engine
     */
    fun switchEngine(context: Context, engineName: String): VideoEngineManager {
        synchronized(this) {
            // Release current instance
            instance = null
            
            // Update config
            val config = VideoEngineConfig(context)
            config.selectedEngine = engineName
            
            // Create new instance
            instance = createEngineManager(context)
            return instance!!
        }
    }
    
    /**
     * Get available engine names
     */
    fun getAvailableEngines(): List<String> {
        return listOf(
            "ExoPlayer"
            // Future engines:
            // "VLC",
            // "IJKPlayer"
        )
    }
}