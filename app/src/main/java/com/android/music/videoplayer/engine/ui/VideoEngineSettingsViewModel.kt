package com.android.music.videoplayer.engine.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.music.videoplayer.engine.core.VideoEngineInfo
import com.android.music.videoplayer.engine.core.VideoEngineUpdateResult
import com.android.music.videoplayer.engine.manager.VideoEngineManager
import com.android.music.videoplayer.engine.manager.VideoEngineManagerFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for video engine settings management.
 */
class VideoEngineSettingsViewModel : ViewModel() {
    
    private var engineManager: VideoEngineManager? = null
    
    private val _engineInfo = MutableStateFlow(VideoEngineInfo.notInstalled("ExoPlayer"))
    val engineInfo: StateFlow<VideoEngineInfo> = _engineInfo.asStateFlow()
    
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
    
    private val _updateProgress = MutableStateFlow(0)
    val updateProgress: StateFlow<Int> = _updateProgress.asStateFlow()
    
    private val _updateResult = MutableStateFlow<VideoEngineUpdateResult?>(null)
    val updateResult: StateFlow<VideoEngineUpdateResult?> = _updateResult.asStateFlow()
    
    private val _availableEngines = MutableStateFlow<List<String>>(emptyList())
    val availableEngines: StateFlow<List<String>> = _availableEngines.asStateFlow()
    
    private val _selectedEngine = MutableStateFlow("ExoPlayer")
    val selectedEngine: StateFlow<String> = _selectedEngine.asStateFlow()
    
    fun initialize(context: Context) {
        engineManager = VideoEngineManagerFactory.getInstance(context)
        _availableEngines.value = VideoEngineManagerFactory.getAvailableEngines()
        
        viewModelScope.launch {
            engineManager?.initialize()
            
            // Observe engine info changes
            engineManager?.engineInfo?.collect { info ->
                _engineInfo.value = info
                _selectedEngine.value = info.name
            }
        }
        
        viewModelScope.launch {
            engineManager?.isUpdating?.collect { updating ->
                _isUpdating.value = updating
            }
        }
        
        viewModelScope.launch {
            engineManager?.updateProgress?.collect { progress ->
                _updateProgress.value = progress
            }
        }
    }
    
    fun checkForUpdates() {
        viewModelScope.launch {
            engineManager?.checkForUpdates(forceCheck = true)
        }
    }
    
    fun installOrUpdateEngine() {
        viewModelScope.launch {
            engineManager?.installOrUpdate()?.collect { result ->
                _updateResult.value = result
            }
        }
    }
    
    fun uninstallEngine() {
        viewModelScope.launch {
            engineManager?.uninstall()
        }
    }
    
    fun switchEngine(context: Context, engineName: String) {
        viewModelScope.launch {
            try {
                // Release current engine
                engineManager = null
                
                // Switch to new engine
                engineManager = VideoEngineManagerFactory.switchEngine(context, engineName)
                _selectedEngine.value = engineName
                
                // Initialize new engine
                engineManager?.initialize()
                
                // Observe new engine
                engineManager?.engineInfo?.collect { info ->
                    _engineInfo.value = info
                }
            } catch (e: Exception) {
                _updateResult.value = VideoEngineUpdateResult.Failed("Failed to switch engine: ${e.message}")
            }
        }
    }
    
    fun clearUpdateResult() {
        _updateResult.value = null
    }
}