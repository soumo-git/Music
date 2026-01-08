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
                            
                            // Get the actual playlist title
                            val playlistTitle = extractPlaylistTitle(response.out) 
                                ?: getPlaylistTitle(url)
                                ?: "Playlist"
                            
                            // Sanitize for display (keep emojis for UI, sanitization happens at download time)
                            val displayTitle = "$playlistTitle (${playlistItems.size} items)"
                            
                            val platform = SupportedPlatform.fromUrl(url)
                            val content = ExtractedContent(
                                url = url,
                                title = displayTitle,
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
        
        // Check if it's a playlist URL
        val isPlaylistUrl = url.contains("playlist") || url.contains("list=")
        
        // Get playlist info first if it's a playlist
        var totalPlaylistItems = 0
        var completedPlaylistItems = 0
        var currentItemTitle: String? = null
        
        if (isPlaylistUrl) {
            // Try to get playlist item count
            try {
                val infoRequest = YoutubeDLRequest(url)
                infoRequest.addOption("--flat-playlist")
                infoRequest.addOption("--dump-json")
                val infoResponse = YoutubeDL.getInstance().execute(infoRequest)
                if (infoResponse.exitCode == 0) {
                    totalPlaylistItems = infoResponse.out.split("\n").count { it.trim().startsWith("{") }
                    Log.d(TAG, "Playlist has $totalPlaylistItems items")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get playlist info: ${e.message}")
            }
        }
        
        trySend(DownloadProgress(
            downloadId = downloadId,
            progress = 0,
            downloadedBytes = 0,
            totalBytes = format.fileSize ?: 0,
            speed = null,
            eta = null,
            status = DownloadProgressStatus.STARTING,
            isPlaylist = isPlaylistUrl,
            totalItems = totalPlaylistItems,
            completedItems = 0,
            currentItemTitle = null
        ))
        
        try {
            if (!isInitialized) {
                initialize()
            }
            
            activeDownloads[downloadId] = processId
            
            val request = YoutubeDLRequest(url)
            
            // Determine if outputPath is a directory or file
            val outputFile = File(outputPath)
            val isDirectory = outputFile.isDirectory || !outputPath.contains(".")
            
            // Output path configuration
            val outputTemplate: String
            val baseDirectory: String
            
            if (isPlaylistUrl && isDirectory) {
                // For playlists: get the playlist title and create folder
                val playlistTitle = getPlaylistTitle(url)
                val sanitizedTitle = sanitizeForFilename(playlistTitle ?: "Playlist")
                val playlistFolder = File(outputPath, sanitizedTitle)
                
                // Create the folder if it doesn't exist
                if (!playlistFolder.exists()) {
                    playlistFolder.mkdirs()
                }
                
                baseDirectory = outputPath
                // No playlist index - just use title
                outputTemplate = "${playlistFolder.absolutePath}/%(title)s.%(ext)s"
                Log.d(TAG, "Playlist folder: ${playlistFolder.absolutePath}")
            } else if (isDirectory) {
                // Single video but outputPath is a directory
                baseDirectory = outputPath
                outputTemplate = "$outputPath/%(title)s.%(ext)s"
            } else {
                // Single video with full file path
                baseDirectory = outputFile.parent ?: outputPath
                outputTemplate = outputPath
            }
            request.addOption("-o", outputTemplate)
            
            // Restrict filenames to ASCII characters (avoids unicode issues in filenames)
            request.addOption("--restrict-filenames")
            
            // Enable playlist downloading
            request.addOption("--yes-playlist")
            
            // Print progress to stdout for parsing
            request.addOption("--newline")
            request.addOption("--progress")
            
            // Universal download archive - skip already downloaded files across all downloads
            val archiveFile = File(baseDirectory, ".download_archive.txt")
            request.addOption("--download-archive", archiveFile.absolutePath)
            
            // Continue partial downloads
            request.addOption("--continue")
            
            // Retry on errors
            request.addOption("--retries", "5")
            
            // Ignore errors (continue with other items if one fails)
            request.addOption("--ignore-errors")
            
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
            
            // Don't preserve original file modification time
            request.addOption("--no-mtime")
            
            Log.d(TAG, "Starting download: $url -> $outputTemplate with format: ${format.formatId}")
            
            val response = YoutubeDL.getInstance().execute(
                request,
                processId
            ) { progress, etaInSeconds, line ->
                // Parse the output line for playlist progress
                val lineStr = line ?: ""
                
                // Check for "[download] Downloading video X of Y"
                val playlistProgressPattern = "\\[download\\]\\s+Downloading\\s+(?:video|item)\\s+(\\d+)\\s+of\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE)
                val playlistMatch = playlistProgressPattern.find(lineStr)
                if (playlistMatch != null) {
                    val currentItem = playlistMatch.groupValues[1].toIntOrNull() ?: 0
                    val total = playlistMatch.groupValues[2].toIntOrNull() ?: totalPlaylistItems
                    completedPlaylistItems = currentItem - 1 // Current is being downloaded, so completed = current - 1
                    if (total > 0) totalPlaylistItems = total
                    Log.d(TAG, "Playlist progress: $currentItem of $totalPlaylistItems")
                }
                
                // Check for "[download] Destination:" to get current item title
                val destinationPattern = "\\[download\\]\\s+Destination:\\s+.*/([^/]+)\\.\\w+$".toRegex()
                val destMatch = destinationPattern.find(lineStr)
                if (destMatch != null) {
                    currentItemTitle = destMatch.groupValues[1].replace("_", " ")
                    Log.d(TAG, "Current item: $currentItemTitle")
                }
                
                // Check for "has already been downloaded" to count completed items
                if (lineStr.contains("has already been downloaded") || lineStr.contains("has already been recorded")) {
                    completedPlaylistItems++
                    Log.d(TAG, "Item already downloaded, completed: $completedPlaylistItems")
                }
                
                // Check for download completion of an item
                if (lineStr.contains("[download] 100%") || lineStr.contains("100.0%")) {
                    if (isPlaylistUrl && totalPlaylistItems > 0) {
                        completedPlaylistItems++
                        Log.d(TAG, "Item completed, total completed: $completedPlaylistItems")
                    }
                }
                
                // Calculate overall progress for playlists
                val overallProgress = if (isPlaylistUrl && totalPlaylistItems > 0) {
                    val itemProgress = progress.toInt()
                    val baseProgress = (completedPlaylistItems * 100) / totalPlaylistItems
                    val currentItemContribution = (itemProgress * 100) / (totalPlaylistItems * 100)
                    minOf(baseProgress + currentItemContribution, 99)
                } else {
                    progress.toInt()
                }
                
                trySend(DownloadProgress(
                    downloadId = downloadId,
                    progress = overallProgress,
                    downloadedBytes = 0,
                    totalBytes = format.fileSize ?: 0,
                    speed = null,
                    eta = if (etaInSeconds > 0) "${etaInSeconds}s" else null,
                    status = DownloadProgressStatus.DOWNLOADING,
                    isPlaylist = isPlaylistUrl,
                    totalItems = totalPlaylistItems,
                    completedItems = completedPlaylistItems,
                    currentItemTitle = currentItemTitle
                ))
            }
            
            activeDownloads.remove(downloadId)
            
            if (response.exitCode == 0) {
                // Trigger MediaStore scan so files appear in gallery
                val scanDir = File(baseDirectory)
                if (scanDir.exists()) {
                    // Scan the entire download directory and subdirectories
                    val filesToScan = mutableListOf<String>()
                    scanDir.walkTopDown().forEach { file ->
                        if (file.isFile && !file.name.startsWith(".")) {
                            filesToScan.add(file.absolutePath)
                        }
                    }
                    
                    if (filesToScan.isNotEmpty()) {
                        MediaScannerConnection.scanFile(
                            context,
                            filesToScan.toTypedArray(),
                            arrayOf("video/mp4", "audio/mpeg", "audio/mp3"),
                            null
                        )
                        Log.d(TAG, "Triggered MediaStore scan for ${filesToScan.size} files")
                    }
                }
                
                trySend(DownloadProgress(
                    downloadId = downloadId,
                    progress = 100,
                    downloadedBytes = format.fileSize ?: 0,
                    totalBytes = format.fileSize ?: 0,
                    speed = null,
                    eta = null,
                    status = DownloadProgressStatus.COMPLETED,
                    isPlaylist = isPlaylistUrl,
                    totalItems = totalPlaylistItems,
                    completedItems = totalPlaylistItems,
                    currentItemTitle = null
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
                    status = DownloadProgressStatus.FAILED,
                    isPlaylist = isPlaylistUrl,
                    totalItems = totalPlaylistItems,
                    completedItems = completedPlaylistItems,
                    currentItemTitle = null
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
                status = DownloadProgressStatus.FAILED,
                isPlaylist = isPlaylistUrl,
                totalItems = totalPlaylistItems,
                completedItems = completedPlaylistItems,
                currentItemTitle = null
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
    
    /**
     * Extract playlist title from JSON output
     */
    private fun extractPlaylistTitle(jsonOutput: String): String? {
        try {
            // Look for playlist_title in the JSON
            val lines = jsonOutput.split("\n").filter { it.trim().startsWith("{") }
            for (line in lines) {
                val playlistTitle = extractJsonValue(line, "playlist_title")
                if (!playlistTitle.isNullOrBlank() && playlistTitle != "NA") {
                    return playlistTitle
                }
                // Also try "playlist" field as fallback
                val playlist = extractJsonValue(line, "playlist")
                if (!playlist.isNullOrBlank() && playlist != "NA" && !playlist.contains("_items_")) {
                    return playlist
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract playlist title: ${e.message}")
        }
        return null
    }
    
    /**
     * Get playlist title from URL using yt-dlp
     */
    private suspend fun getPlaylistTitle(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("--dump-single-json")
            request.addOption("--no-warnings")
            
            val response = YoutubeDL.getInstance().execute(request)
            
            if (response.exitCode == 0 && response.out.isNotEmpty()) {
                // Try to extract playlist title from JSON
                val json = response.out
                val title = extractJsonValue(json, "title") 
                    ?: extractJsonValue(json, "playlist_title")
                
                if (!title.isNullOrBlank() && title != "NA") {
                    Log.d(TAG, "Got playlist title: $title")
                    return@withContext title
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get playlist title: ${e.message}")
        }
        null
    }
    
    /**
     * Sanitize a string for use as a filename/folder name
     * Removes or replaces invalid characters while preserving readability
     */
    private fun sanitizeForFilename(name: String): String {
        // First, remove everything that's not ASCII letters, numbers, spaces, or basic punctuation
        val asciiOnly = name.map { char ->
            when {
                char.code in 32..126 -> char // Printable ASCII
                else -> ' ' // Replace non-ASCII with space
            }
        }.joinToString("")
        
        // Replace invalid filename characters with spaces
        val sanitized = asciiOnly
            .replace(Regex("[<>:\"/\\\\|?*]"), " ")
            .replace(Regex("\\s+"), " ")  // Collapse multiple spaces
            .trim()
        
        // If the result is empty or too short, use a default
        return if (sanitized.length < 2) {
            "Playlist"
        } else {
            // Limit length to 100 characters
            sanitized.take(100)
        }
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
