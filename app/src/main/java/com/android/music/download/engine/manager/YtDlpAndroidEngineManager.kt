package com.android.music.download.engine.manager

import android.content.Context
import android.util.Log
import com.android.music.download.engine.config.EngineConfig
import com.android.music.download.engine.core.DownloadEngine
import com.android.music.download.engine.core.EngineInfo
import com.android.music.download.engine.core.EngineUpdateResult
import com.android.music.download.engine.ytdlp.YtDlpAndroidEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * Engine manager for youtubedl-android library.
 * This library embeds Python and yt-dlp, so no separate binary download is needed.
 */
class YtDlpAndroidEngineManager(
    private val context: Context
) : EngineManager {
    
    private val config = EngineConfig(context)
    private var engine: YtDlpAndroidEngine? = null
    
    private val _engineInfo = MutableStateFlow(EngineInfo.notInstalled(ENGINE_NAME))
    override val engineInfo: StateFlow<EngineInfo> = _engineInfo.asStateFlow()
    
    private val _isUpdating = MutableStateFlow(false)
    override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
    
    private val _updateProgress = MutableStateFlow(0)
    override val updateProgress: StateFlow<Int> = _updateProgress.asStateFlow()
    
    companion object {
        private const val TAG = "YtDlpAndroidEngineManager"
        private const val ENGINE_NAME = "yt-dlp"
        private const val DEFAULT_VERSION = "2024.12.16" // Default bundled version
    }
    
    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing yt-dlp engine manager...")
                
                // Check if engine is manually installed by user
                val isEngineInstalled = config.installedVersion != null
                
                if (isEngineInstalled) {
                    // Create and initialize the engine only if manually installed
                    engine = YtDlpAndroidEngine(context)
                    val initResult = engine!!.initialize()
                    
                    if (initResult.isSuccess) {
                        val version = engine!!.getInstalledVersion() ?: DEFAULT_VERSION
                        config.installedVersion = version
                        config.lastVersionCheck = System.currentTimeMillis()
                        
                        _engineInfo.value = EngineInfo(
                            name = ENGINE_NAME,
                            installedVersion = version,
                            latestVersion = version,
                            isInstalled = true,
                            isUpdateAvailable = false,
                            lastChecked = config.lastVersionCheck,
                            binaryPath = null
                        )
                        
                        Log.d(TAG, "yt-dlp engine initialized successfully. Version: $version")
                    } else {
                        Log.e(TAG, "yt-dlp engine initialization failed: ${initResult.exceptionOrNull()?.message}")
                        // Reset installation status if initialization fails
                        config.clear()
                        _engineInfo.value = EngineInfo.notInstalled(ENGINE_NAME)
                    }
                } else {
                    // Engine not installed - user must manually install from settings
                    _engineInfo.value = EngineInfo.notInstalled(ENGINE_NAME)
                    Log.d(TAG, "yt-dlp engine not installed - user must install manually")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize yt-dlp engine manager", e)
                _engineInfo.value = EngineInfo.notInstalled(ENGINE_NAME)
            }
        }
    }
    
    override fun getEngine(): DownloadEngine? = engine
    
    override suspend fun checkForUpdates(forceCheck: Boolean): EngineInfo = withContext(Dispatchers.IO) {
        try {
            // The library handles updates internally
            // Just return current info
            val currentVersion = engine?.getInstalledVersion() ?: DEFAULT_VERSION
            config.installedVersion = currentVersion
            config.lastVersionCheck = System.currentTimeMillis()
            
            _engineInfo.value = _engineInfo.value.copy(
                installedVersion = currentVersion,
                lastChecked = config.lastVersionCheck
            )
            
            Log.d(TAG, "Version check completed. Current: $currentVersion")
            _engineInfo.value
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            _engineInfo.value
        }
    }
    
    override fun installOrUpdate(): Flow<EngineUpdateResult> = flow {
        if (_isUpdating.value) {
            emit(EngineUpdateResult.Failed("Update already in progress"))
            return@flow
        }
        
        _isUpdating.value = true
        _updateProgress.value = 0
        emit(EngineUpdateResult.Downloading)
        
        try {
            // Initialize engine if not already done
            if (engine == null) {
                engine = YtDlpAndroidEngine(context)
            }
            
            _updateProgress.value = 30
            
            // Initialize the engine (this extracts the bundled yt-dlp)
            val initResult = engine!!.initialize()
            
            if (initResult.isFailure) {
                emit(EngineUpdateResult.Failed("Failed to initialize: ${initResult.exceptionOrNull()?.message}"))
                return@flow
            }
            
            _updateProgress.value = 60
            
            // Try to update to latest version
            val updateResult = engine!!.updateYtDlp()
            
            _updateProgress.value = 100
            
            val version = engine!!.getInstalledVersion() ?: DEFAULT_VERSION
            config.installedVersion = version
            config.lastVersionCheck = System.currentTimeMillis()
            
            _engineInfo.value = EngineInfo(
                name = ENGINE_NAME,
                installedVersion = version,
                latestVersion = version,
                isInstalled = true,
                isUpdateAvailable = false,
                lastChecked = config.lastVersionCheck,
                binaryPath = null
            )
            
            if (updateResult.isSuccess) {
                emit(EngineUpdateResult.Success(updateResult.getOrNull() ?: version))
                Log.d(TAG, "Engine updated successfully to: $version")
            } else {
                // Even if update fails, engine is still usable
                emit(EngineUpdateResult.Success(version))
                Log.d(TAG, "Engine ready (update skipped). Version: $version")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Update failed", e)
            emit(EngineUpdateResult.Failed("Update failed: ${e.message}", e))
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
                    if (file.name.startsWith("youtubedl")) {
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
        withContext(Dispatchers.IO) {
            // Clear engine and config
            engine = null
            config.clear()
            _engineInfo.value = EngineInfo.notInstalled(ENGINE_NAME)
            Log.d(TAG, "yt-dlp engine uninstalled")
        }
    }
}
