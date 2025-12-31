# Video Engine Architecture - Replaceable Engine System

## Overview

The video player has been refactored to use a replaceable engine system similar to the download engine. This allows users to switch between different video playback engines (ExoPlayer, VLC, IJKPlayer, etc.) and manage engine versions through the settings.

## Architecture Components

### 1. Core Interface (`VideoEngine.kt`)
- **Purpose**: Defines the contract for all video engines
- **Key Methods**:
  - `initialize()`: Initialize the engine
  - `loadVideo()`: Load video with optional separate audio
  - `play()`, `pause()`, `stop()`: Playback controls
  - `seekTo()`, `setPlaybackSpeed()`, `setVolume()`: Advanced controls
  - `attachToPlayerView()`: Bind to UI component
  - `getInstalledVersion()`, `isInstalled()`: Version management

### 2. Engine Implementation (`ExoPlayerEngine.kt`)
- **Purpose**: ExoPlayer implementation of the VideoEngine interface
- **Features**:
  - Media3 ExoPlayer integration
  - Support for merged video/audio streams
  - Quality selection and playback speed control
  - State management via StateFlow
  - Error handling and recovery

### 3. Engine Manager (`VideoEngineManager.kt` + `ExoPlayerEngineManager.kt`)
- **Purpose**: Manages engine lifecycle, updates, and version checking
- **Features**:
  - Engine initialization and cleanup
  - Version checking and update management
  - Progress tracking for updates
  - Cache management

### 4. Configuration (`VideoEngineConfig.kt`)
- **Purpose**: Persistent storage for engine settings
- **Settings**:
  - Selected engine, installed version, update preferences
  - Video quality, playback speed, volume defaults
  - Auto-play, controls visibility, buffer size
  - Hardware acceleration preferences

### 5. Factory Pattern (`VideoEngineManagerFactory.kt`)
- **Purpose**: Creates and manages engine instances
- **Features**:
  - Singleton pattern for engine managers
  - Engine switching capability
  - Available engines enumeration

### 6. Settings UI (`VideoEngineSettingsFragment.kt` + `VideoEngineSettingsViewModel.kt`)
- **Purpose**: User interface for engine management
- **Features**:
  - Engine selection dropdown
  - Version display and update status
  - Update progress tracking
  - Engine switching with live updates

## Key Features

### ✅ Replaceable Engine System
- Switch between different video engines (currently ExoPlayer, extensible for VLC, IJKPlayer)
- Factory pattern ensures clean engine switching
- Configuration persists engine selection

### ✅ Version Management
- Display current installed version
- Check for updates (currently shows bundled version)
- Update progress tracking with percentage
- Warning system for outdated engines

### ✅ Settings Integration
- Tabbed settings interface (Download Engine | Video Engine)
- Real-time engine status updates
- Engine switching without app restart
- Persistent configuration storage

### ✅ Backward Compatibility
- Existing VideoPlayerActivity continues to work
- Gradual migration path from old VideoPlayerEngine
- Same API surface for video playback

## Usage Examples

### Engine Management in Settings
```kotlin
// Initialize engine manager
val engineManager = VideoEngineManagerFactory.getInstance(context)
engineManager.initialize()

// Check for updates
val engineInfo = engineManager.checkForUpdates(forceCheck = true)

// Install or update engine
engineManager.installOrUpdate().collect { result ->
    when (result) {
        is VideoEngineUpdateResult.Success -> // Handle success
        is VideoEngineUpdateResult.Failed -> // Handle error
        VideoEngineUpdateResult.Downloading -> // Show progress
    }
}
```

### Video Playback
```kotlin
// Get current engine
val engine = VideoEngineManagerFactory.getInstance(context).getEngine()

// Load and play video
engine?.attachToPlayerView(playerView)
engine?.loadVideo(videoUrl, audioUrl)
engine?.play()
```

### Engine Switching
```kotlin
// Switch to different engine
val newEngineManager = VideoEngineManagerFactory.switchEngine(context, "VLC")
newEngineManager.initialize()
```

## Extension Points

### Adding New Engines

1. **Create Engine Implementation**:
```kotlin
class VLCEngine(context: Context) : VideoEngine {
    override val engineName = "VLC"
    // Implement all VideoEngine methods
}
```

2. **Create Engine Manager**:
```kotlin
class VLCEngineManager(context: Context) : VideoEngineManager {
    // Implement lifecycle management
}
```

3. **Update Factory**:
```kotlin
// In VideoEngineManagerFactory
"VLC" -> VLCEngineManager(context.applicationContext)
```

4. **Add to Available Engines**:
```kotlin
fun getAvailableEngines(): List<String> {
    return listOf("ExoPlayer", "VLC", "IJKPlayer")
}
```

## File Structure

```
app/src/main/java/com/android/music/videoplayer/
├── engine/
│   ├── core/
│   │   └── VideoEngine.kt                 # Core interface & data classes
│   ├── exoplayer/
│   │   └── ExoPlayerEngine.kt            # ExoPlayer implementation
│   ├── manager/
│   │   ├── VideoEngineManager.kt         # Manager interface
│   │   ├── ExoPlayerEngineManager.kt     # ExoPlayer manager
│   │   └── VideoEngineManagerFactory.kt  # Factory for managers
│   ├── config/
│   │   └── VideoEngineConfig.kt          # Configuration storage
│   └── ui/
│       ├── VideoEngineSettingsFragment.kt    # Settings UI
│       └── VideoEngineSettingsViewModel.kt   # Settings ViewModel
├── ui/
│   ├── VideoPlayerActivity.kt            # Updated to use new system
│   └── VideoPlayerViewModel.kt           # Updated to use new system
└── core/
    └── VideoPlayerEngine.kt              # Legacy (can be deprecated)
```

## Migration Path

### Phase 1: ✅ Complete
- Implement new engine architecture
- Create ExoPlayer engine implementation
- Add settings UI with version management
- Update existing components to use new system

### Phase 2: Future
- Add VLC engine implementation
- Add IJKPlayer engine implementation
- Implement actual version checking for engines
- Add engine-specific settings and optimizations

### Phase 3: Future
- Deprecate old VideoPlayerEngine
- Add advanced engine features (hardware decoding, codec selection)
- Implement engine performance monitoring
- Add engine recommendation system

## Benefits

1. **Modularity**: Clean separation between engine interface and implementation
2. **Extensibility**: Easy to add new video engines without changing existing code
3. **User Control**: Users can choose their preferred engine and manage versions
4. **Maintainability**: Each engine is self-contained and independently testable
5. **Future-Proof**: Architecture supports advanced features like codec selection, hardware acceleration
6. **Consistency**: Same pattern as download engine for familiar user experience

## Technical Notes

- **Thread Safety**: All engine operations are thread-safe using coroutines
- **State Management**: StateFlow provides reactive UI updates
- **Error Handling**: Comprehensive error handling with user-friendly messages
- **Performance**: Lazy initialization and singleton patterns minimize resource usage
- **Testing**: Interface-based design enables easy unit testing and mocking