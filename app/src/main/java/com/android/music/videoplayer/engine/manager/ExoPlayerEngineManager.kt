package com.android.music.videoplayer.engine.manager

import android.content.Context
import android.util.Log
import com.android.music.videoplayer.engine.config.VideoEngineConfig
import com.android.music.videoplayer.engine.core.*
import com.android.music.videoplayer.engine.exoplayer.ExoPlayerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * Engine manager for ExoPlayer (Media3).
 * ExoPlayer is bundled with the app, so no separate installation is needed.
 */
class ExoPlayerEngineManager(
    private val context: Context
) : VideoEngineManager {
    
    private val config = VideoEngineConfig(context)
    private var engine: ExoPlayerEngine? = null
    
    private val _engineInfo = MutableStateFlow(VideoEngineInfo.notInstalled(ENGINE_NAME))
    override val engineInfo: StateFlow<VideoEngineInfo> = _engineInfo.asStateFlow()
    
    private val _isUpdating = MutableStateFlow(false)
    override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
    
    private val _updateProgress = MutableStateFlow(0)
    override val updateProgress: StateFlow<Int> = _updateProgress.asStateFlow()
    
    companion object {
        private const val TAG = "ExoPlayerEngineManager"
        private const val ENGINE_NAME = "ExoPlayer"
        private const val DEFAULT_VERSION = "1.2.0" // Media3 ExoPlayer version
    }
    
    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing ExoPlayer engine manager...")
                
                // Check if engine is manually installed by user
                val isEngineInstalled = config.installedVersion != null
                
                if (isEngineInstalled) {
                    // Create and initialize the engine only if manually installed
                    engine = ExoPlayerEngine(context)
                    val initResult = engine!!.initialize()
                    
                    if (initResult.isSuccess) {
                        val version = engine!!.getInstalledVersion() ?: DEFAULT_VERSION
                        config.installedVersion = version
                        config.lastVersionCheck = System.currentTimeMillis()
                        
                        _engineInfo.value = VideoEngineInfo(
                            name = ENGINE_NAME,
                            installedVersion = version,
                            latestVersion = version,
                            isInstalled = true,
                            isUpdateAvailable = false,
                            lastChecked = config.lastVersionCheck,
                            enginePath = null
                        )
                        
                        Log.d(TAG, "ExoPlayer engine initialized successfully. Version: $version")
                    } else {
                        Log.e(TAG, "ExoPlayer engine initialization failed: ${initResult.exceptionOrNull()?.message}")
                        // Reset installation status if initialization fails
                        config.clear()
                        _engineInfo.value = VideoEngineInfo.notInstalled(ENGINE_NAME)
                    }
                } else {
                    // Engine not installed - user must manually install from settings
                    _engineInfo.value = VideoEngineInfo.notInstalled(ENGINE_NAME)
                    Log.d(TAG, "ExoPlayer engine not installed - user must install manually")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ExoPlayer engine manager", e)
                _engineInfo.value = VideoEngineInfo.notInstalled(ENGINE_NAME)
            }
        }
    }
    
    override fun getEngine(): VideoEngine? = engine
    
    override suspend fun checkForUpdates(forceCheck: Boolean): VideoEngineInfo = withContext(Dispatchers.IO) {
        try {
            // ExoPlayer is bundled with the app, so we just check the current version
            val currentVersion = engine?.getInstalledVersion() ?: DEFAULT_VERSION
            config.installedVersion = currentVersion
            config.lastVersionCheck = System.currentTimeMillis()
            
            // For bundled engines, we could check for app updates instead
            // For now, we'll just return the current version as latest
            _engineInfo.value = _engineInfo.value.copy(
                installedVersion = currentVersion,
                latestVersion = currentVersion,
                isUpdateAvailable = false,
                lastChecked = config.lastVersionCheck
            )
            
            Log.d(TAG, "Version check completed. Current: $currentVersion")
            _engineInfo.value
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            _engineInfo.value
        }
    }
    
    override fun installOrUpdate(): Flow<VideoEngineUpdateResult> = flow {
        if (_isUpdating.value) {
            emit(VideoEngineUpdateResult.Failed("Update already in progress"))
            return@flow
        }
        
        _isUpdating.value = true
        _updateProgress.value = 0
        emit(VideoEngineUpdateResult.Downloading)
        
        try {
            // Create engine if not already done
            if (engine == null) {
                engine = ExoPlayerEngine(context)
            }
            
            _updateProgress.value = 30
            
            // Initialize the engine (this "installs" it)
            val initResult = engine!!.initialize()
            
            if (initResult.isFailure) {
                emit(VideoEngineUpdateResult.Failed("Failed to initialize: ${initResult.exceptionOrNull()?.message}"))
                return@flow
            }
            
            _updateProgress.value = 60
            
            val version = engine!!.getInstalledVersion() ?: DEFAULT_VERSION
            config.installedVersion = version
            config.lastVersionCheck = System.currentTimeMillis()
            
            _updateProgress.value = 100
            
            _engineInfo.value = VideoEngineInfo(
                name = ENGINE_NAME,
                installedVersion = version,
                latestVersion = version,
                isInstalled = true,
                isUpdateAvailable = false,
                lastChecked = config.lastVersionCheck,
                enginePath = null
            )
            
            emit(VideoEngineUpdateResult.Success(version))
            Log.d(TAG, "ExoPlayer engine installed successfully. Version: $version")
            
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            emit(VideoEngineUpdateResult.Failed("Installation failed: ${e.message}", e))
        } finally {
            _isUpdating.value = false
            _updateProgress.value = 0
        }
    }.flowOn(Dispatchers.IO)
    
    override fun getEnginePath(): String = context.filesDir.absolutePath
    
    override suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            // Clear any cached files
            try {
                val cacheDir = context.cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("exoplayer")) {
                        file.deleteRecursively()
                    }
                }
                Log.d(TAG, "Cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear cache", e)
            }
        }
    }
    
    override suspend fun uninstall() {
        withContext(Dispatchers.Main) {
            // Release engine on main thread and clear config
            engine?.release()
            engine = null
        }
        withContext(Dispatchers.IO) {
            config.clear()
            _engineInfo.value = VideoEngineInfo.notInstalled(ENGINE_NAME)
            Log.d(TAG, "ExoPlayer engine uninstalled")
        }
    }
}