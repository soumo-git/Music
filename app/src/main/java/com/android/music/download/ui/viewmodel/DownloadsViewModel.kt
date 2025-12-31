package com.android.music.download.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.music.download.data.model.DownloadFormat
import com.android.music.download.data.model.DownloadItem
import com.android.music.download.data.model.DownloadStatus
import com.android.music.download.data.model.ExtractedContent
import com.android.music.download.engine.core.DownloadProgressStatus
import com.android.music.download.engine.core.EngineInfo
import com.android.music.download.engine.core.EngineNotInstalledException
import com.android.music.download.engine.manager.EngineManager
import com.android.music.download.engine.manager.EngineManagerFactory
import com.android.music.download.manager.DownloadStateManager
import com.android.music.videoplayer.preview.PreviewManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import com.google.gson.Gson

/**
 * ViewModel for managing downloads.
 * Handles extraction, download queue, and download state.
 */
class DownloadsViewModel : ViewModel() {

    companion object {
        private const val TAG = "DownloadsViewModel"
    }

    private var engineManager: EngineManager? = null
    private var downloadDirectory: String = ""
    private lateinit var preferences: SharedPreferences
    private val gson = Gson()
    private var appContext: Context? = null

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _downloads = MutableLiveData<List<DownloadItem>>(emptyList())
    val downloads: LiveData<List<DownloadItem>> = _downloads

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _extractionSuccess = MutableLiveData<ExtractedContent?>()
    val extractionSuccess: LiveData<ExtractedContent?> = _extractionSuccess

    private val _engineInfo = MutableLiveData<EngineInfo?>()
    val engineInfo: LiveData<EngineInfo?> = _engineInfo

    private val _showEngineSetup = MutableLiveData<Boolean>(false)
    val showEngineSetup: LiveData<Boolean> = _showEngineSetup
    
    // Preview ready event - emits when preview URLs are ready to play
    private val _previewReady = MutableLiveData<PreviewData?>()
    val previewReady: LiveData<PreviewData?> = _previewReady
    
    /**
     * Data class for preview data
     */
    data class PreviewData(
        val videoUrl: String,
        val audioUrl: String?,
        val title: String,
        val startPositionMs: Long
    )

    private var isInitialized = false

    /**
     * Initialize the ViewModel with context
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext
        
        engineManager = EngineManagerFactory.getInstance(context)
        preferences = context.getSharedPreferences("downloads_prefs", Context.MODE_PRIVATE)
        
        // Use public Downloads directory so files appear in gallery
        val downloadDir = File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "Music")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        downloadDirectory = downloadDir.absolutePath

        // Load any previously saved completed downloads
        loadSavedDownloads()
        
        viewModelScope.launch {
            engineManager?.initialize()
            engineManager?.engineInfo?.collectLatest { info ->
                _engineInfo.postValue(info)
                _showEngineSetup.postValue(!info.isInstalled)
            }
        }
    }

    private fun loadSavedDownloads() {
        val dir = File(downloadDirectory)
        if (!dir.exists()) {
            _downloads.value = emptyList()
            return
        }

        val files = dir.listFiles() ?: emptyArray()
        val items = files
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                DownloadItem(
                    id = file.absolutePath,
                    title = file.nameWithoutExtension,
                    url = file.absolutePath,
                    thumbnailUrl = null,
                    duration = null,
                    author = null,
                    platform = "Local",
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    fileSize = file.length(),
                    downloadedSize = file.length(),
                    createdAt = java.util.Date(file.lastModified()),
                    completedAt = java.util.Date(file.lastModified()),
                    filePath = file.absolutePath,
                    errorMessage = null
                )
            }

        _downloads.value = items
    }

    /**
     * Public API to force-reload downloads from persistent storage
     */
    fun reloadFromStorage() {
        loadSavedDownloads()
    }

    private fun persistDownloads() {
        val current = _downloads.value?.filter { it.status == DownloadStatus.COMPLETED } ?: emptyList()
        val json = gson.toJson(current)
        preferences.edit().putString("downloads_history", json).apply()
    }

    /**
     * Extract content from URL with preview URL caching.
     * PreviewManager handles cache clearing internally when URL changes.
     */
    fun extractContent(url: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val context = appContext
                if (context == null) {
                    _error.value = "Context not available"
                    return@launch
                }
                
                Log.d(TAG, "Extracting content: $url")
                
                // Use PreviewManager for extraction with preview caching
                val result = PreviewManager.extractWithPreview(context, url)
                
                result.onSuccess { content ->
                    Log.d(TAG, "Extraction successful: ${content.title}")
                    _extractionSuccess.value = content
                }.onFailure { e ->
                    Log.e(TAG, "Extraction failed: ${e.message}")
                    when (e) {
                        is EngineNotInstalledException -> {
                            _showEngineSetup.value = true
                            _error.value = "Download engine not installed. Please install it first."
                        }
                        else -> _error.value = "Failed to extract: ${e.message}"
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Extract failed: ${e.message}")
                _error.value = "Failed to extract content: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Start preview for extracted content.
     * Uses cached URLs for instant playback.
     */
    fun startPreview(content: ExtractedContent) {
        // Check if we have cached preview URLs
        val cached = PreviewManager.getCachedPreview(content.url)
        
        if (cached != null) {
            Log.d(TAG, "Using cached preview URLs (instant)")
            _previewReady.value = PreviewData(
                videoUrl = cached.videoUrl,
                audioUrl = cached.audioUrl,
                title = cached.title,
                startPositionMs = cached.savedPositionMs
            )
        } else if (content.cachedVideoUrl != null) {
            // Use URLs from extracted content
            Log.d(TAG, "Using extracted content URLs")
            PreviewManager.cachePreview(
                originalUrl = content.url,
                videoUrl = content.cachedVideoUrl,
                audioUrl = content.cachedAudioUrl,
                title = content.title
            )
            _previewReady.value = PreviewData(
                videoUrl = content.cachedVideoUrl,
                audioUrl = content.cachedAudioUrl,
                title = content.title,
                startPositionMs = 0L
            )
        } else {
            // No cached URLs - need to fetch (slower path)
            Log.d(TAG, "No cached URLs, fetching...")
            fetchPreviewUrls(content)
        }
    }
    
    /**
     * Fetch preview URLs when not cached (fallback path).
     */
    private fun fetchPreviewUrls(content: ExtractedContent) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val engine = engineManager?.getEngine()
                if (engine == null) {
                    _showEngineSetup.value = true
                    _error.value = "Download engine not installed"
                    return@launch
                }
                
                val ytDlpEngine = engine as? com.android.music.download.engine.ytdlp.YtDlpAndroidEngine
                if (ytDlpEngine == null) {
                    _error.value = "Preview not supported with current engine"
                    return@launch
                }
                
                Log.d(TAG, "Fetching streaming URLs for: ${content.url}")
                val result = ytDlpEngine.getStreamingUrls(content.url)
                
                result.onSuccess { streamingUrls ->
                    // Cache for future use
                    PreviewManager.cachePreview(
                        originalUrl = content.url,
                        videoUrl = streamingUrls.videoUrl,
                        audioUrl = streamingUrls.audioUrl,
                        title = content.title
                    )
                    
                    _previewReady.value = PreviewData(
                        videoUrl = streamingUrls.videoUrl,
                        audioUrl = streamingUrls.audioUrl,
                        title = content.title,
                        startPositionMs = 0L
                    )
                }.onFailure { e ->
                    _error.value = "Failed to get streaming URL: ${e.message}"
                }
                
            } catch (e: Exception) {
                _error.value = "Failed to get streaming URL: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clear preview ready event after handling.
     */
    fun clearPreviewReady() {
        _previewReady.value = null
    }
    
    /**
     * Extract content and start download in one operation.
     */
    fun extractAndDownload(url: String, formatId: String, videoId: String) {
        viewModelScope.launch {
            try {
                val engine = engineManager?.getEngine()
                if (engine == null) {
                    _showEngineSetup.value = true
                    _error.value = "Download engine not installed. Please install it first."
                    DownloadStateManager.setState(videoId, DownloadStateManager.DownloadState.FAILED)
                    return@launch
                }
                
                Log.d(TAG, "Extracting content for download: $url")
                
                val result = engine.extractContent(url)
                
                result.onSuccess { content ->
                    Log.d(TAG, "Extraction successful, starting download")
                    DownloadStateManager.setState(videoId, DownloadStateManager.DownloadState.DOWNLOADING, 0, formatId)
                    startDownloadWithVideoId(content, formatId, videoId)
                }.onFailure { e ->
                    Log.e(TAG, "Extraction failed: ${e.message}")
                    DownloadStateManager.setState(videoId, DownloadStateManager.DownloadState.FAILED)
                    when (e) {
                        is EngineNotInstalledException -> {
                            _showEngineSetup.value = true
                            _error.value = "Download engine not installed. Please install it first."
                        }
                        else -> _error.value = "Failed to extract: ${e.message}"
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Extract and download failed: ${e.message}")
                DownloadStateManager.setState(videoId, DownloadStateManager.DownloadState.FAILED)
                _error.value = "Failed to extract content: ${e.message}"
            }
        }
    }
    
    private fun startDownloadWithVideoId(content: ExtractedContent, formatId: String, videoId: String) {
        viewModelScope.launch {
            try {
                val engine = engineManager?.getEngine()
                if (engine == null) {
                    _showEngineSetup.value = true
                    _error.value = "Download engine not installed"
                    DownloadStateManager.setState(videoId, DownloadStateManager.DownloadState.FAILED)
                    return@launch
                }
                
                val format = content.formats.find { it.formatId == formatId } ?: DownloadFormat(
                    formatId = formatId,
                    extension = if (formatId == "bestaudio") "mp3" else "mp4",
                    quality = if (formatId == "bestaudio") "Audio Only" else "Best Quality",
                    fileSize = null,
                    hasAudio = true,
                    hasVideo = formatId != "bestaudio",
                    resolution = null
                )
                
                val downloadId = UUID.randomUUID().toString()
                val outputPath = "$downloadDirectory/${sanitizeFilename(content.title)}.${format.extension}"
                
                val downloadItem = DownloadItem(
                    id = downloadId,
                    title = content.title,
                    url = content.url,
                    thumbnailUrl = content.thumbnailUrl,
                    duration = content.duration,
                    author = content.author,
                    platform = content.platform,
                    status = DownloadStatus.DOWNLOADING,
                    fileSize = format.fileSize,
                    filePath = outputPath
                )
                
                val currentDownloads = _downloads.value?.toMutableList() ?: mutableListOf()
                currentDownloads.add(0, downloadItem)
                _downloads.value = currentDownloads
                
                Log.d(TAG, "Starting download: ${content.title}")
                
                engine.download(content.url, format, outputPath).collectLatest { progress ->
                    when (progress.status) {
                        DownloadProgressStatus.DOWNLOADING, DownloadProgressStatus.PROCESSING -> {
                            DownloadStateManager.setState(videoId, DownloadStateManager.DownloadState.DOWNLOADING, progress.progress, formatId)
                        }
                        DownloadProgressStatus.COMPLETED -> {
                            DownloadStateManager.setState(videoId, DownloadStateManager.DownloadState.COMPLETED)
                        }
                        DownloadProgressStatus.FAILED, DownloadProgressStatus.CANCELLED -> {
                            DownloadStateManager.setState(videoId, DownloadStateManager.DownloadState.FAILED)
                        }
                        else -> {}
                    }
                    updateDownloadProgress(downloadId, progress)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                DownloadStateManager.setState(videoId, DownloadStateManager.DownloadState.FAILED)
                _error.value = "Failed to start download: ${e.message}"
            }
        }
    }

    /**
     * Start download for extracted content
     */
    fun startDownload(content: ExtractedContent, formatId: String) {
        viewModelScope.launch {
            try {
                val engine = engineManager?.getEngine()
                if (engine == null) {
                    _showEngineSetup.value = true
                    _error.value = "Download engine not installed"
                    return@launch
                }
                
                val format = content.formats.find { it.formatId == formatId }
                if (format == null) {
                    _error.value = "Invalid format selected"
                    return@launch
                }
                
                val downloadId = UUID.randomUUID().toString()
                val outputPath = "$downloadDirectory/${sanitizeFilename(content.title)}.${format.extension}"
                
                val downloadItem = DownloadItem(
                    id = downloadId,
                    title = content.title,
                    url = content.url,
                    thumbnailUrl = content.thumbnailUrl,
                    duration = content.duration,
                    author = content.author,
                    platform = content.platform,
                    status = DownloadStatus.DOWNLOADING,
                    fileSize = format.fileSize,
                    filePath = outputPath
                )
                
                val currentDownloads = _downloads.value?.toMutableList() ?: mutableListOf()
                currentDownloads.add(0, downloadItem)
                _downloads.value = currentDownloads
                persistDownloads()
                
                engine.download(content.url, format, outputPath).collectLatest { progress ->
                    updateDownloadProgress(downloadId, progress)
                }
                
            } catch (e: Exception) {
                _error.value = "Failed to start download: ${e.message}"
            }
        }
    }

    private fun updateDownloadProgress(
        downloadId: String,
        progress: com.android.music.download.engine.core.DownloadProgress
    ) {
        val currentDownloads = _downloads.value?.toMutableList() ?: return
        val index = currentDownloads.indexOfFirst { it.id == downloadId }
        if (index == -1) return
        
        val item = currentDownloads[index]
        val newStatus = when (progress.status) {
            DownloadProgressStatus.STARTING -> DownloadStatus.DOWNLOADING
            DownloadProgressStatus.DOWNLOADING -> DownloadStatus.DOWNLOADING
            DownloadProgressStatus.PROCESSING -> DownloadStatus.DOWNLOADING
            DownloadProgressStatus.COMPLETED -> DownloadStatus.COMPLETED
            DownloadProgressStatus.FAILED -> DownloadStatus.FAILED
            DownloadProgressStatus.CANCELLED -> DownloadStatus.CANCELLED
        }
        
        currentDownloads[index] = item.copy(
            progress = progress.progress,
            downloadedSize = progress.downloadedBytes,
            status = newStatus,
            completedAt = if (newStatus == DownloadStatus.COMPLETED) java.util.Date() else null
        )
        
        _downloads.postValue(currentDownloads)
        persistDownloads()
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            val currentDownloads = _downloads.value?.toMutableList() ?: return@launch
            val index = currentDownloads.indexOfFirst { it.id == downloadId }
            if (index != -1) {
                currentDownloads[index] = currentDownloads[index].copy(status = DownloadStatus.PAUSED)
                _downloads.value = currentDownloads
                persistDownloads()
            }
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch {
            val download = _downloads.value?.find { it.id == downloadId } ?: return@launch
            extractContent(download.url)
        }
    }

    fun cancelDownload(downloadId: String) {
        viewModelScope.launch {
            engineManager?.getEngine()?.cancelDownload(downloadId)
            
            val currentDownloads = _downloads.value?.toMutableList() ?: return@launch
            val index = currentDownloads.indexOfFirst { it.id == downloadId }
            if (index != -1) {
                currentDownloads[index] = currentDownloads[index].copy(status = DownloadStatus.CANCELLED)
                _downloads.value = currentDownloads
                persistDownloads()
            }
        }
    }

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch {
            val currentDownloads = _downloads.value?.toMutableList() ?: return@launch
            val download = currentDownloads.find { it.id == downloadId }
            
            download?.filePath?.let { path ->
                try {
                    java.io.File(path).delete()
                } catch (e: Exception) {
                    // Ignore file deletion errors
                }
            }
            
            currentDownloads.removeAll { it.id == downloadId }
            _downloads.value = currentDownloads
            persistDownloads()
        }
    }

    fun searchDownloads(query: String) {
        viewModelScope.launch {
            val allDownloads = _downloads.value ?: return@launch
            val filtered = allDownloads.filter { download ->
                download.title.contains(query, ignoreCase = true) ||
                download.author?.contains(query, ignoreCase = true) == true ||
                download.platform.contains(query, ignoreCase = true)
            }
            _downloads.value = filtered
        }
    }

    fun clearSearch() {
        // Reload from storage to restore full list
        loadSavedDownloads()
    }

    fun clearError() {
        _error.value = null
    }

    fun clearExtractionSuccess() {
        _extractionSuccess.value = null
    }

    fun dismissEngineSetup() {
        _showEngineSetup.value = false
    }
}
