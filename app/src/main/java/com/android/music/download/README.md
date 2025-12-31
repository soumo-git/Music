# Download Module

This module handles content extraction and downloading from various streaming platforms using the **youtubedl-android** library.

## Architecture

The download module follows a clean architecture pattern with yt-dlp embedded via the youtubedl-android library:

```
download/
├── data/
│   ├── model/          # Data models (DownloadItem, ExtractedContent, etc.)
│   └── repository/     # Data access layer
├── engine/
│   ├── core/           # Core engine interfaces (DownloadEngine, EngineInfo)
│   ├── config/         # Engine configuration and preferences
│   ├── manager/        # Engine lifecycle management
│   ├── ytdlp/          # yt-dlp Android implementation
│   └── ui/             # Engine settings UI
├── service/            # Background download service
└── ui/
    ├── fragment/       # UI fragments
    ├── viewmodel/      # ViewModels
    └── adapter/        # RecyclerView adapters
```

## youtubedl-android Library

We use the **youtubedl-android** library which embeds Python and yt-dlp for native Android execution:

```kotlin
// build.gradle.kts
val youtubedlAndroid = "0.18.1"
implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")
```

### Why youtubedl-android?

1. **Embedded Python**: No need to install Python separately on Android
2. **Native Integration**: Works seamlessly with Android's lifecycle
3. **Active Maintenance**: Large community, frequent updates
4. **FFmpeg Support**: Built-in audio/video processing
5. **1000+ Sites**: Supports YouTube, Instagram, TikTok, Twitter, and many more

## Engine Lifecycle

```
App Start → Initialize YoutubeDL
    ├── Extract bundled yt-dlp
    ├── Initialize FFmpeg
    └── Ready to use

Update Flow:
    User clicks "Update" → YoutubeDL.updateYoutubeDL()
    └── Downloads latest yt-dlp version
```

## Components

### Engine Layer

- **YtDlpAndroidEngine**: Main engine using youtubedl-android library
- **YtDlpAndroidEngineManager**: Manages engine initialization and updates
- **EngineConfig**: Stores engine preferences and version info

### Data Layer

- **DownloadItem**: Represents a download with status, progress, and metadata
- **ExtractedContent**: Represents extracted content information before download
- **DownloadFormat**: Available quality/format options for download

### UI Layer

- **DownloadsFragment**: Main UI for downloads management
- **DownloadsViewModel**: Manages UI state and business logic
- **DownloadsAdapter**: RecyclerView adapter for download items
- **EngineSettingsFragment**: UI for engine management

## Supported Platforms

The yt-dlp engine supports 1000+ websites including:

- YouTube, YouTube Music
- Dailymotion
- Instagram
- TikTok
- Twitter/X
- Facebook
- Vimeo
- SoundCloud
- Twitch
- Reddit
- And many more...

## Usage Example

```kotlin
// Initialize engine
val engine = YtDlpAndroidEngine(context)
engine.initialize()

// Extract content
val result = engine.extractContent("https://youtube.com/watch?v=...")
result.onSuccess { content ->
    // Show format selection
    content.formats.forEach { format ->
        println("${format.quality} - ${format.extension}")
    }
}

// Download
engine.download(url, selectedFormat, outputPath).collect { progress ->
    println("Progress: ${progress.progress}%")
}
```

## Implementation Status

### ✅ Completed
- youtubedl-android library integration
- Engine initialization and version management
- Content extraction with format selection
- Download progress tracking
- Downloads list with status indicators
- Engine settings UI
- Update functionality

### 🚧 To Be Implemented
- Background download service with notifications
- Local database for download history
- Download queue management
- File management and storage options

## Best Practices

- **Initialization**: Call `engine.initialize()` before any operations
- **Error Handling**: Always handle extraction/download failures gracefully
- **Progress Updates**: Use Flow to observe download progress
- **Cancellation**: Support download cancellation via `cancelDownload()`
