package com.android.music.download.engine.ytdlp

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.android.music.download.data.model.DownloadFormat
import com.android.music.download.data.model.ExtractedContent
import com.android.music.download.engine.core.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * yt-dlp Android engine implementation using youtubedl-android library.
 * This library embeds Python and yt-dlp for native Android execution.
 */
class YtDlpAndroidEngine(
    private val context: Context
) : DownloadEngine {
    
    override val engineName: String = "yt-dlp-android"
    
    private var isInitialized = false
    private val activeDownloads = ConcurrentHashMap<String, String>() // downloadId -> processId
    
    companion object {
        private const val TAG = "YtDlpAndroidEngine"
    }
    
    /**
     * Initialize the yt-dlp and FFmpeg libraries
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                return@withContext Result.success(Unit)
            }
            
            Log.d(TAG, "Initializing YoutubeDL...")
            YoutubeDL.getInstance().init(context)
            
            Log.d(TAG, "Initializing FFmpeg...")
            FFmpeg.getInstance().init(context)
            
            isInitialized = true
            Log.d(TAG, "YoutubeDL and FFmpeg initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YoutubeDL", e)
            Result.failure(EngineException("Failed to initialize download engine: ${e.message}", e))
        }
    }
    
    override suspend fun getInstalledVersion(): String? = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                initialize()
            }
            val version = YoutubeDL.getInstance().version(context)
            Log.d(TAG, "yt-dlp version retrieved: $version")
            version
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get yt-dlp version: ${e.message}")
            null
        }
    }
    
    override suspend fun isInstalled(): Boolean {
        return try {
            if (!isInitialized) {
                initialize().isSuccess
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun extractContent(url: String): Result<ExtractedContent> = withContext(Dispatchers.IO) {
        extractContent(url, prefetchStreamingUrls = false)
    }
    
    /**
     * Extract content with optional streaming URL pre-fetch
     * @param prefetchStreamingUrls If true, pre-fetches streaming URLs for instant preview (slower but better UX for preview)
     */
    suspend fun extractContent(url: String, prefetchStreamingUrls: Boolean): Result<ExtractedContent> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return@withContext Result.failure(initResult.exceptionOrNull()!!)
                }
            }
            
            Log.d(TAG, "Extracting content from: $url")
            
            // Check if it's a playlist URL
            val isPlaylist = url.contains("playlist") || url.contains("list=") || 
                            url.contains("/playlist/") || url.contains("dailymotion")
            
            if (isPlaylist) {
                // For playlists, use a special request to get all items
                val request = YoutubeDLRequest(url)
                request.addOption("--flat-playlist")
                request.addOption("--dump-json")
                
                try {
                    val response = YoutubeDL.getInstance().execute(request)
                    
                    if (response.exitCode == 0 && response.out.isNotEmpty()) {
                        // Parse JSON to extract playlist info
                        val playlistItems = parsePlaylistJson(response.out, url)
                        
                        if (playlistItems.isNotEmpty()) {
                            Log.d(TAG, "Extracted playlist with ${playlistItems.size} items")
                            
                            val platform = SupportedPlatform.fromUrl(url)
                            val content = ExtractedContent(
                                url = url,
                                title = "Playlist (${playlistItems.size} items)",
                                thumbnailUrl = playlistItems.firstOrNull()?.thumbnailUrl,
                                duration = null,
                                author = null,
                                platform = platform.displayName,
                                formats = emptyList(),
                                isPlaylist = true,
                                playlistItems = playlistItems
                            )
                            return@withContext Result.success(content)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract playlist with flat-playlist: ${e.message}")
                }
            }
            
            // Fall back to single video extraction
            val videoInfo = YoutubeDL.getInstance().getInfo(url)
            
            Log.d(TAG, "Extracted: ${videoInfo.title}")
            
            val formats = parseFormats(videoInfo)
            val platform = SupportedPlatform.fromUrl(url)
            
            // Pre-fetch streaming URLs only if requested (for preview optimization)
            // Use the already extracted videoInfo to avoid double extraction
            var cachedVideoUrl: String? = null
            var cachedAudioUrl: String? = null
            
            if (prefetchStreamingUrls) {
                Log.d(TAG, "Extracting streaming URLs from video info (no extra network call)...")
                
                // Try to get streaming URLs from the already extracted formats
                val videoFormats = videoInfo.formats ?: emptyList()
                
                // Find best video-only format that device can handle (max 1080p for compatibility)
                val bestVideoFormat = videoFormats
                    .filter { format ->
                        val hasVideo = format.vcodec != null && format.vcodec != "none"
                        val noAudio = format.acodec == null || format.acodec == "none"
                        val hasUrl = !format.url.isNullOrEmpty()
                        val height = format.height?.toString()?.toIntOrNull() ?: 0
                        // Limit to 1080p max for device compatibility
                        val isPlayableResolution = height <= 1920
                        hasVideo && noAudio && hasUrl && isPlayableResolution
                    }
                    .sortedByDescending { it.height?.toString()?.toIntOrNull() ?: 0 }
                    .firstOrNull()
                
                // Find best audio-only format
                val bestAudioFormat = videoFormats
                    .filter { format ->
                        val hasAudio = format.acodec != null && format.acodec != "none"
                        val noVideo = format.vcodec == null || format.vcodec == "none"
                        val hasUrl = !format.url.isNullOrEmpty()
                        hasAudio && noVideo && hasUrl
                    }
                    .sortedByDescending { it.tbr?.toString()?.toDoubleOrNull() ?: 0.0 }
                    .firstOrNull()
                
                if (bestVideoFormat != null && bestAudioFormat != null) {
                    // Separate video and audio streams for highest quality
                    cachedVideoUrl = bestVideoFormat.url
                    cachedAudioUrl = bestAudioFormat.url
                    Log.d(TAG, "Got separate video (${bestVideoFormat.height}p) and audio streams from video info")
                } else {
                    // Fallback: Find best combined format (max 1080p)
                    val bestCombinedFormat = videoFormats
                        .filter { format ->
                            val hasVideo = format.vcodec != null && format.vcodec != "none"
                            val hasAudio = format.acodec != null && format.acodec != "none"
                            val hasUrl = !format.url.isNullOrEmpty()
                            val height = format.height?.toString()?.toIntOrNull() ?: 0
                            // Limit to 1080p max for device compatibility
                            val isPlayableResolution = height <= 1920
                            hasVideo && hasAudio && hasUrl && isPlayableResolution
                        }
                        .sortedByDescending { it.height?.toString()?.toIntOrNull() ?: 0 }
                        .firstOrNull()
                    
                    if (bestCombinedFormat != null) {
                        cachedVideoUrl = bestCombinedFormat.url
                        Log.d(TAG, "Got combined format (${bestCombinedFormat.height}p) from video info")
                    } else {
                        // Last fallback: try any format with video+audio regardless of resolution
                        val anyFormat = videoFormats
                            .filter { format ->
                                val hasVideo = format.vcodec != null && format.vcodec != "none"
                                val hasAudio = format.acodec != null && format.acodec != "none"
                                val hasUrl = !format.url.isNullOrEmpty()
                                hasVideo && hasAudio && hasUrl
                            }
                            .sortedBy { it.height?.toString()?.toIntOrNull() ?: 0 } // Sort ascending to get lowest quality
                            .firstOrNull()
                        
                        if (anyFormat != null) {
                            cachedVideoUrl = anyFormat.url
                            Log.d(TAG, "Using lowest quality format (${anyFormat.height}p) for compatibility")
                        } else if (videoInfo.url != null) {
                            // Absolute last fallback: use the main URL
                            cachedVideoUrl = videoInfo.url
                            Log.d(TAG, "Using main URL from video info")
                        }
                    }
                }
                
                if (cachedVideoUrl != null) {
                    Log.d(TAG, "Cached streaming URLs for instant preview")
                } else {
                    Log.w(TAG, "Failed to cache streaming URLs from video info")
                }
            }
            
            val content = ExtractedContent(
                url = url,
                title = videoInfo.title ?: "Unknown",
                thumbnailUrl = videoInfo.thumbnail,
                duration = formatDuration(videoInfo.duration?.toLong()),
                author = videoInfo.uploader,
                platform = platform.displayName,
                formats = formats,
                isPlaylist = false,
                playlistItems = emptyList(),
                cachedVideoUrl = cachedVideoUrl,
                cachedAudioUrl = cachedAudioUrl
            )
            
            Result.success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed", e)
            Result.failure(ExtractionFailedException("Failed to extract content: ${e.message}", e))
        }
    }
    
    private fun parsePlaylistJson(jsonOutput: String, playlistUrl: String): List<ExtractedContent> {
        val items = mutableListOf<ExtractedContent>()
        val platform = SupportedPlatform.fromUrl(playlistUrl)
        
        try {
            // Split by newlines to get individual JSON objects
            val lines = jsonOutput.split("\n").filter { it.trim().startsWith("{") }
            
            for (line in lines) {
                try {
                    val id = extractJsonValue(line, "id") ?: continue
                    val title = extractJsonValue(line, "title") ?: "Unknown"
                    val url = extractJsonValue(line, "url") ?: "https://www.youtube.com/watch?v=$id"
                    val thumbnail = extractJsonValue(line, "thumbnail")
                    val duration = extractJsonValue(line, "duration")?.toLongOrNull()
                    
                    items.add(ExtractedContent(
                        url = url,
                        title = title,
                        thumbnailUrl = thumbnail,
                        duration = formatDuration(duration),
                        author = null,
                        platform = platform.displayName,
                        formats = parseFormats(null),
                        isPlaylist = false,
                        playlistItems = emptyList()
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse playlist item: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing playlist JSON: ${e.message}")
        }
        
        return items
    }
    
    override fun download(
        url: String,
        format: DownloadFormat,
        outputPath: String
    ): Flow<DownloadProgress> = callbackFlow {
        val downloadId = UUID.randomUUID().toString()
        val processId = "download_$downloadId"
        
        trySend(DownloadProgress(
            downloadId = downloadId,
            progress = 0,
            downloadedBytes = 0,
            totalBytes = format.fileSize ?: 0,
            speed = null,
            eta = null,
            status = DownloadProgressStatus.STARTING
        ))
        
        try {
            if (!isInitialized) {
                initialize()
            }
            
            activeDownloads[downloadId] = processId
            
            val request = YoutubeDLRequest(url)
            
            // Output path with playlist support - add number for each item
            val outputTemplate = if (url.contains("playlist") || url.contains("list=")) {
                "$outputPath/%(playlist)s/%(playlist_index)s - %(title)s.%(ext)s"
            } else {
                outputPath
            }
            request.addOption("-o", outputTemplate)
            
            // Enable playlist downloading
            request.addOption("--yes-playlist")
            
            // Format selection - always use best quality
            when {
                format.formatId == "bestaudio" -> {
                    // Audio only - best audio quality
                    request.addOption("-f", "bestaudio")
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                }
                format.formatId == "bestvideo" -> {
                    // Video only - best video quality
                    request.addOption("-f", "bestvideo")
                }
                else -> {
                    // Best combined (video + audio) - download best video and audio separately and merge
                    request.addOption("-f", "bestvideo+bestaudio/best")
                    request.addOption("--merge-output-format", "mp4")
                }
            }
            
            // Embed metadata and thumbnail
            request.addOption("--embed-metadata")
            request.addOption("--embed-thumbnail")
            
            Log.d(TAG, "Starting download: $url -> $outputTemplate with format: ${format.formatId}")
            
            val response = YoutubeDL.getInstance().execute(
                request,
                processId
            ) { progress, etaInSeconds, _ ->
                // This callback is called on progress updates - emit immediately
                Log.d(TAG, "Progress: $progress%, ETA: ${etaInSeconds}s")
                
                trySend(DownloadProgress(
                    downloadId = downloadId,
                    progress = progress.toInt(),
                    downloadedBytes = 0,
                    totalBytes = format.fileSize ?: 0,
                    speed = null,
                    eta = if (etaInSeconds > 0) "${etaInSeconds}s" else null,
                    status = DownloadProgressStatus.DOWNLOADING
                ))
            }
            
            activeDownloads.remove(downloadId)
            
            if (response.exitCode == 0) {
                // Trigger MediaStore scan so files appear in gallery
                val outputDir = File(outputPath).parentFile
                if (outputDir?.exists() == true) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outputDir.absolutePath),
                        arrayOf("video/mp4", "audio/mpeg"),
                        null
                    )
                    Log.d(TAG, "Triggered MediaStore scan for: ${outputDir.absolutePath}")
                }
                
                trySend(DownloadProgress(
                    downloadId = downloadId,
                    progress = 100,
                    downloadedBytes = format.fileSize ?: 0,
                    totalBytes = format.fileSize ?: 0,
                    speed = null,
                    eta = null,
                    status = DownloadProgressStatus.COMPLETED
                ))
                Log.d(TAG, "Download completed: $outputPath")
            } else {
                trySend(DownloadProgress(
                    downloadId = downloadId,
                    progress = 0,
                    downloadedBytes = 0,
                    totalBytes = 0,
                    speed = null,
                    eta = response.err,
                    status = DownloadProgressStatus.FAILED
                ))
                Log.e(TAG, "Download failed: ${response.err}")
            }
        } catch (e: Exception) {
            activeDownloads.remove(downloadId)
            Log.e(TAG, "Download error", e)
            trySend(DownloadProgress(
                downloadId = downloadId,
                progress = 0,
                downloadedBytes = 0,
                totalBytes = 0,
                speed = null,
                eta = e.message,
                status = DownloadProgressStatus.FAILED
            ))
        }
        
        awaitClose {
            activeDownloads.remove(downloadId)
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun cancelDownload(downloadId: String) {
        val processId = activeDownloads[downloadId]
        if (processId != null) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
                activeDownloads.remove(downloadId)
                Log.d(TAG, "Cancelled download: $downloadId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel download", e)
            }
        }
    }
    
    override fun supportsUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
    
    /**
     * Update yt-dlp to the latest version
     */
    suspend fun updateYtDlp(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                initialize()
            }
            
            Log.d(TAG, "Updating yt-dlp...")
            YoutubeDL.getInstance().updateYoutubeDL(context)
            
            val newVersion = getInstalledVersion() ?: "unknown"
            Log.d(TAG, "Update completed. New version: $newVersion")
            Result.success(newVersion)
        } catch (e: Exception) {
            Log.e(TAG, "Update error", e)
            Result.failure(EngineException("Failed to update: ${e.message}", e))
        }
    }
    
    /**
     * Check if a URL is a playlist
     */
    suspend fun isPlaylistUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                initialize()
            }
            
            // YouTube playlist URLs contain "playlist" or "list=" parameter
            if (url.contains("playlist") || url.contains("list=")) {
                return@withContext true
            }
            
            // Dailymotion playlist URLs
            if (url.contains("dailymotion") && url.contains("/playlist/")) {
                return@withContext true
            }
            
            return@withContext false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking if URL is playlist: ${e.message}")
            false
        }
    }
    
    /**
     * Data class to hold streaming URLs (video and optional audio)
     */
    data class StreamingUrls(
        val videoUrl: String,
        val audioUrl: String? = null
    )
    
    /**
     * Get direct streaming URL for video preview
     * Returns the highest quality format - may return separate video and audio URLs
     */
    suspend fun getStreamingUrl(url: String): Result<String> = withContext(Dispatchers.IO) {
        val result = getStreamingUrls(url)
        result.map { it.videoUrl }
    }
    
    /**
     * Get streaming URLs for video preview (video + audio separately for highest quality)
     * Returns StreamingUrls with videoUrl and optional audioUrl
     */
    suspend fun getStreamingUrls(url: String): Result<StreamingUrls> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return@withContext Result.failure(initResult.exceptionOrNull()!!)
                }
            }
            
            Log.d(TAG, "Getting streaming URLs for: $url")
            
            // Use yt-dlp to get the best quality URLs
            val request = YoutubeDLRequest(url)
            
            // Request best video+audio - this returns two URLs for highest quality
            request.addOption("-f", "bestvideo+bestaudio/best")
            request.addOption("-g") // Get URL only
            
            Log.d(TAG, "Requesting best quality format with yt-dlp...")
            val response = YoutubeDL.getInstance().execute(request)
            
            if (response.exitCode == 0 && response.out.isNotEmpty()) {
                val urls = response.out.trim().split("\n").filter { it.isNotBlank() }
                
                if (urls.size >= 2) {
                    // Two URLs: first is video, second is audio
                    Log.d(TAG, "Got separate video and audio URLs for highest quality")
                    Log.d(TAG, "Video URL: ${urls[0].take(100)}...")
                    Log.d(TAG, "Audio URL: ${urls[1].take(100)}...")
                    return@withContext Result.success(StreamingUrls(
                        videoUrl = urls[0],
                        audioUrl = urls[1]
                    ))
                } else if (urls.size == 1) {
                    // Single URL with both video and audio
                    Log.d(TAG, "Got combined video+audio URL: ${urls[0].take(100)}...")
                    return@withContext Result.success(StreamingUrls(videoUrl = urls[0]))
                }
            }
            
            // Fallback 1: Try to get video info and find best combined format
            Log.d(TAG, "Fallback: Getting video info...")
            val videoInfo = YoutubeDL.getInstance().getInfo(url)
            val formats = videoInfo.formats ?: emptyList()
            
            // Log available formats for debugging
            Log.d(TAG, "Available formats: ${formats.size}")
            formats.take(10).forEach { format ->
                Log.d(TAG, "Format: ${format.formatId} - ${format.format} - " +
                        "Resolution: ${format.height}p - " +
                        "Video: ${format.vcodec} - Audio: ${format.acodec} - " +
                        "Bitrate: ${format.tbr}")
            }
            
            // Find the best format that has both video and audio in one stream (max 1080p for compatibility)
            val bestCombinedFormat = formats
                .filter { format ->
                    val hasVideo = format.vcodec != null && format.vcodec != "none"
                    val hasAudio = format.acodec != null && format.acodec != "none"
                    val hasUrl = !format.url.isNullOrEmpty()
                    val height = format.height?.toString()?.toIntOrNull() ?: 0
                    // Limit to 1080p max for device compatibility
                    val isPlayableResolution = height <= 1920
                    hasVideo && hasAudio && hasUrl && isPlayableResolution
                }
                .sortedWith(compareByDescending<com.yausername.youtubedl_android.mapper.VideoFormat> { 
                    it.height?.toString()?.toIntOrNull() ?: 0 
                }.thenByDescending { 
                    it.tbr?.toString()?.toDoubleOrNull() ?: 0.0 
                })
                .firstOrNull()
            
            if (bestCombinedFormat != null) {
                val formatUrl = bestCombinedFormat.url
                if (!formatUrl.isNullOrEmpty()) {
                    Log.d(TAG, "Got combined format URL: ${formatUrl.take(100)}...")
                    Log.d(TAG, "Format: ${bestCombinedFormat.format} - Resolution: ${bestCombinedFormat.height}p")
                    return@withContext Result.success(StreamingUrls(videoUrl = formatUrl))
                }
            }
            
            // Fallback 2: Try lower quality formats if high quality failed
            Log.d(TAG, "Fallback 2: Trying lower quality formats for compatibility...")
            val lowerQualityFormat = formats
                .filter { format ->
                    val hasVideo = format.vcodec != null && format.vcodec != "none"
                    val hasAudio = format.acodec != null && format.acodec != "none"
                    val hasUrl = !format.url.isNullOrEmpty()
                    hasVideo && hasAudio && hasUrl
                }
                .sortedBy { it.height?.toString()?.toIntOrNull() ?: 0 } // Sort ascending to get lowest quality
                .firstOrNull()
            
            if (lowerQualityFormat != null) {
                val formatUrl = lowerQualityFormat.url
                if (!formatUrl.isNullOrEmpty()) {
                    Log.d(TAG, "Got lower quality format URL: ${formatUrl.take(100)}...")
                    Log.d(TAG, "Format: ${lowerQualityFormat.format} - Resolution: ${lowerQualityFormat.height}p")
                    return@withContext Result.success(StreamingUrls(videoUrl = formatUrl))
                }
            }
            
            // Fallback 3: Try just "best" format
            Log.d(TAG, "Fallback 3: Trying 'best' format...")
            val request2 = YoutubeDLRequest(url)
            request2.addOption("-f", "best")
            request2.addOption("-g")
            
            val response2 = YoutubeDL.getInstance().execute(request2)
            if (response2.exitCode == 0 && response2.out.isNotEmpty()) {
                val streamUrl = response2.out.trim().split("\n").firstOrNull()?.trim()
                if (!streamUrl.isNullOrEmpty()) {
                    Log.d(TAG, "Got streaming URL from 'best' format: ${streamUrl.take(100)}...")
                    return@withContext Result.success(StreamingUrls(videoUrl = streamUrl))
                }
            }
            
            // Last fallback: use URL from video info
            val streamUrl = videoInfo.url
            if (!streamUrl.isNullOrEmpty()) {
                Log.d(TAG, "Got streaming URL from video info: ${streamUrl.take(100)}...")
                return@withContext Result.success(StreamingUrls(videoUrl = streamUrl))
            }
            
            Result.failure(ExtractionFailedException("Could not get streaming URL"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get streaming URL", e)
            Result.failure(ExtractionFailedException("Failed to get streaming URL: ${e.message}", e))
        }
    }
    
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }
    
    private fun parseFormats(videoInfo: com.yausername.youtubedl_android.mapper.VideoInfo?): List<DownloadFormat> {
        val formats = mutableListOf<DownloadFormat>()
        
        // Add best combined format
        formats.add(DownloadFormat(
            formatId = "best",
            extension = "mp4",
            quality = "Best Quality",
            fileSize = null,
            hasAudio = true,
            hasVideo = true,
            resolution = null
        ))
        
        // Add audio-only option
        formats.add(DownloadFormat(
            formatId = "bestaudio",
            extension = "mp3",
            quality = "Audio Only (MP3)",
            fileSize = null,
            hasAudio = true,
            hasVideo = false,
            resolution = null
        ))
        
        return formats
    }
    
    private fun formatDuration(seconds: Long?): String? {
        if (seconds == null || seconds <= 0) return null
        
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
}
