package com.android.music.download.engine.manager

import android.content.Context
import com.android.music.download.engine.core.DownloadEngine
import com.android.music.download.engine.core.EngineInfo
import com.android.music.download.engine.core.EngineUpdateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages download engine lifecycle including installation, updates, and version checking.
 * This is the main entry point for engine management operations.
 */
interface EngineManager {
    
    /**
     * Current engine information as a StateFlow
     */
    val engineInfo: StateFlow<EngineInfo>
    
    /**
     * Whether an update operation is in progress
     */
    val isUpdating: StateFlow<Boolean>
    
    /**
     * Update progress (0-100) when downloading engine
     */
    val updateProgress: StateFlow<Int>
    
    /**
     * Initialize the engine manager
     * Should be called when the app starts
     */
    suspend fun initialize()
    
    /**
     * Get the current download engine instance
     * Returns null if engine is not installed
     */
    fun getEngine(): DownloadEngine?
    
    /**
     * Check for engine updates
     * 
     * @param forceCheck If true, bypasses cache and checks remote
     * @return EngineInfo with latest version information
     */
    suspend fun checkForUpdates(forceCheck: Boolean = false): EngineInfo
    
    /**
     * Install or update the engine to the latest version
     * 
     * @return Flow emitting update progress and result
     */
    fun installOrUpdate(): Flow<EngineUpdateResult>
    
    /**
     * Get the path where engine binaries are stored
     */
    fun getEnginePath(): String
    
    /**
     * Clear engine cache and temporary files
     */
    suspend fun clearCache()
    
    /**
     * Uninstall the engine completely
     */
    suspend fun uninstall()
}

/**
 * Factory for creating EngineManager instances
 */
object EngineManagerFactory {
    
    @Volatile
    private var instance: EngineManager? = null
    
    fun getInstance(context: Context): EngineManager {
        return instance ?: synchronized(this) {
            instance ?: createEngineManager(context).also { instance = it }
        }
    }
    
    private fun createEngineManager(context: Context): EngineManager {
        // Use the Android-native yt-dlp library (embeds Python + yt-dlp)
        return YtDlpAndroidEngineManager(context.applicationContext)
    }
}
