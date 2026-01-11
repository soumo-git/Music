package com.android.music.download.engine.core

/**
 * Information about a download engine
 */
data class EngineInfo(
    val name: String,
    val installedVersion: String?,
    val latestVersion: String?,
    val isInstalled: Boolean,
    val isUpdateAvailable: Boolean,
    val lastChecked: Long,
    val binaryPath: String?
) {
    companion object {
        fun notInstalled(name: String) = EngineInfo(
            name = name,
            installedVersion = null,
            latestVersion = null,
            isInstalled = false,
            isUpdateAvailable = false,
            lastChecked = 0L,
            binaryPath = null
        )
    }
}

/**
 * Engine update result
 */
sealed class EngineUpdateResult {
    data class Success(val newVersion: String) : EngineUpdateResult()
    data class AlreadyUpToDate(val version: String) : EngineUpdateResult()
    data class Failed(val error: String, val cause: Throwable? = null) : EngineUpdateResult()
    object Downloading : EngineUpdateResult()
}

/**
 * Supported platforms/extractors
 */
enum class SupportedPlatform(
    val displayName: String,
    val urlPatterns: List<String>
) {
    YOUTUBE("YouTube", listOf("youtube.com", "youtu.be", "youtube-nocookie.com")),
    DAILYMOTION("Dailymotion", listOf("dailymotion.com", "dai.ly")),
    INSTAGRAM("Instagram", listOf("instagram.com", "instagr.am")),
    TWITTER("Twitter/X", listOf("twitter.com", "x.com")),
    TIKTOK("TikTok", listOf("tiktok.com", "vm.tiktok.com")),
    FACEBOOK("Facebook", listOf("facebook.com", "fb.watch")),
    VIMEO("Vimeo", listOf("vimeo.com")),
    SOUNDCLOUD("SoundCloud", listOf("soundcloud.com")),
    TWITCH("Twitch", listOf("twitch.tv", "clips.twitch.tv")),
    REDDIT("Reddit", listOf("reddit.com", "v.redd.it")),
    GENERIC("Generic", emptyList());
    
    companion object {
        fun fromUrl(url: String): SupportedPlatform {
            val lowerUrl = url.lowercase()
            return SupportedPlatform.entries.find { platform ->
                platform.urlPatterns.any { pattern -> lowerUrl.contains(pattern) }
            } ?: GENERIC
        }
    }
}
