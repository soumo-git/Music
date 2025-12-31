package com.android.music.download.engine.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.music.download.engine.core.EngineInfo
import com.android.music.download.engine.core.EngineUpdateResult
import com.android.music.download.engine.manager.EngineManager
import com.android.music.download.engine.manager.EngineManagerFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for engine settings management.
 */
class EngineSettingsViewModel : ViewModel() {
    
    private var engineManager: EngineManager? = null
    
    private val _engineInfo = MutableStateFlow(EngineInfo.notInstalled("yt-dlp"))
    val engineInfo: StateFlow<EngineInfo> = _engineInfo.asStateFlow()
    
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
    
    private val _updateProgress = MutableStateFlow(0)
    val updateProgress: StateFlow<Int> = _updateProgress.asStateFlow()
    
    private val _updateResult = MutableStateFlow<EngineUpdateResult?>(null)
    val updateResult: StateFlow<EngineUpdateResult?> = _updateResult.asStateFlow()
    
    fun initialize(context: Context) {
        engineManager = EngineManagerFactory.getInstance(context)
        
        viewModelScope.launch {
            engineManager?.initialize()
            
            // Observe engine info changes
            engineManager?.engineInfo?.collect { info ->
                _engineInfo.value = info
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
    
    fun clearUpdateResult() {
        _updateResult.value = null
    }
}
