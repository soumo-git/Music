package com.android.music.videoplayer.engine.manager

import com.android.music.videoplayer.engine.core.VideoEngine
import com.android.music.videoplayer.engine.core.VideoEngineInfo
import com.android.music.videoplayer.engine.core.VideoEngineUpdateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages video engine lifecycle including installation, updates, and version checking.
 * This is the main entry point for video engine management operations.
 */
interface VideoEngineManager {
    
    /**
     * Current engine information as a StateFlow
     */
    val engineInfo: StateFlow<VideoEngineInfo>
    
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
     * Get the current video engine instance
     * Returns null if engine is not installed
     */
    fun getEngine(): VideoEngine?
    
    /**
     * Check for engine updates
     * 
     * @param forceCheck If true, bypasses cache and checks remote
     * @return VideoEngineInfo with latest version information
     */
    suspend fun checkForUpdates(forceCheck: Boolean = false): VideoEngineInfo
    
    /**
     * Install or update the engine to the latest version
     * 
     * @return Flow emitting update progress and result
     */
    fun installOrUpdate(): Flow<VideoEngineUpdateResult>
    
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