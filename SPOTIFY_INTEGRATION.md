# Spotify Web API Integration

This document describes the Spotify integration added to the Android Music Player app.

## Overview

The Spotify integration follows the same architecture pattern as the YouTube implementation, providing a seamless experience for users to browse and play music from Spotify within the app.

## Features Implemented

### 1. **Platform Selection**
- Added Spotify to the Browse tab dropdown (alongside YouTube and Downloads)
- Users can switch between platforms using the platform selector

### 2. **Authentication**
- OAuth 2.0 with PKCE (Proof Key for Code Exchange) flow
- Secure token management with automatic refresh
- Sign-in/Sign-out functionality
- Persistent authentication state

### 3. **UI Tabs**
The Spotify UI contains three main tabs:

#### **Home Tab**
- Featured Playlists: Curated playlists from Spotify
- New Releases: Latest albums and singles
- Recommendations: Personalized track recommendations

#### **Your Library Tab**
- Saved Tracks: User's liked songs
- Saved Albums: User's saved albums
- Playlists: User's created and followed playlists

#### **Profile Tab**
- User profile information (name, email, profile picture)
- Follower count
- Country and subscription type (Free/Premium)
- Sign-out button

### 4. **Search Functionality**
- Unified search bar (same location as YouTube search)
- Search for tracks, albums, artists, and playlists
- Real-time search results
- Search mode toggle

### 5. **Player Integration**
- Tracks are played through the original Spotify player (ToS compliant)
- Player bar positioned in the same location as the current player bar
- Player page follows the app's design language

## Architecture

### **MVVM Pattern**
```
SpotifyViewModel
├── SpotifyRepository
│   ├── SpotifyApiService (Retrofit)
│   └── SpotifyAuthManager (OAuth)
├── SpotifyMapper (API → Domain models)
└── UI Fragments
    ├── SpotifyFragment (Tab container)
    ├── SpotifyHomeFragment
    ├── SpotifyLibraryFragment
    └── SpotifyProfileFragment
```

### **Key Components**

#### **Data Models** (`SpotifyModels.kt`)
- `SpotifyTrack`: Track information
- `SpotifyAlbum`: Album information
- `SpotifyArtist`: Artist information
- `SpotifyPlaylist`: Playlist information
- `SpotifyUserProfile`: User profile data
- `SpotifyHomeContent`: Home page content
- `SpotifySearchResult`: Search results

#### **Authentication** (`SpotifyAuthManager.kt`)
- PKCE OAuth 2.0 flow implementation
- Token storage and refresh
- Auth state management with Kotlin Flow

#### **API Service** (`SpotifyApiService.kt`)
- Retrofit interface for Spotify Web API
- Endpoints for:
  - Browse (featured playlists, new releases, recommendations)
  - Search (tracks, albums, artists, playlists)
  - Library (saved tracks, albums, playlists)
  - User profile
  - Personalization (top tracks, recently played)

#### **Repository** (`SpotifyRepository.kt`)
- Data operations with Flow-based async handling
- Error handling and token management
- Caching support

#### **Mapper** (`SpotifyMapper.kt`)
- Converts API responses to domain models
- Formats durations, dates, and follower counts

#### **Network Module** (`NetworkModule.kt`)
- Separate OkHttp client for Spotify
- OAuth token injection via interceptor
- Logging support for debugging

## Setup Instructions

### 1. **Spotify Developer Account**
1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Create a new app
3. Note your Client ID
4. Add redirect URI: `com.android.music://spotify-callback`

### 2. **Update Configuration**
In `SpotifyAuthManager.kt`, replace:
```kotlin
private const val CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID"
```

### 3. **Add Redirect URI to AndroidManifest.xml**
```xml
<activity android:name=".browse.auth.SpotifyCallbackActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="com.android.music"
            android:host="spotify-callback" />
    </intent-filter>
</activity>
```

### 4. **Implement Token Exchange**
Complete the token exchange implementation in `SpotifyAuthManager.kt`:
- `exchangeCodeForToken()`: Exchange authorization code for access token
- `refreshAccessToken()`: Refresh expired access token

### 5. **Spotify SDK Integration (Optional)**
For native playback, integrate the Spotify Android SDK:
```gradle
implementation 'com.spotify.android:auth:1.2.5'
implementation 'com.spotify.sdk:spotify-player:0.7.2@aar'
```

## API Scopes Required

The app requests the following Spotify scopes:
- `user-read-private`: Read user profile
- `user-read-email`: Read user email
- `user-library-read`: Read saved tracks/albums
- `user-library-modify`: Save/remove tracks/albums
- `playlist-read-private`: Read private playlists
- `playlist-read-collaborative`: Read collaborative playlists
- `playlist-modify-public`: Modify public playlists
- `playlist-modify-private`: Modify private playlists
- `user-top-read`: Read top tracks/artists
- `user-read-recently-played`: Read recently played tracks
- `streaming`: Control playback (if using SDK)
- `user-read-playback-state`: Read playback state
- `user-modify-playback-state`: Control playback

## UI Design

### **Professional & Optimized**
- Material Design 3 components
- Spotify brand colors (#1DB954 green)
- Smooth animations and transitions
- Responsive layouts
- Dark theme support

### **Consistent with App Design**
- Same player bar location
- Unified search bar placement
- Consistent navigation patterns
- Matching card styles and typography

## Playback Compliance

To comply with Spotify's Terms of Service:
- Tracks are played through the official Spotify player
- No audio stream extraction or downloading
- Proper attribution and branding
- User must have Spotify app installed or use Web Playback SDK

## Future Enhancements

1. **Playlist Management**
   - Create/edit/delete playlists
   - Add/remove tracks from playlists
   - Reorder playlist tracks

2. **Social Features**
   - Follow/unfollow artists
   - Share tracks and playlists
   - View friends' activity

3. **Advanced Playback**
   - Queue management
   - Crossfade and gapless playback
   - Lyrics display

4. **Offline Mode**
   - Cache track metadata
   - Offline playlist browsing

5. **Analytics**
   - Listening history
   - Top tracks/artists over time
   - Music taste insights

## Testing

### **Manual Testing Checklist**
- [ ] Platform selection shows Spotify option
- [ ] Sign-in flow completes successfully
- [ ] Home tab loads featured content
- [ ] Library tab shows saved tracks
- [ ] Profile tab displays user information
- [ ] Search returns relevant results
- [ ] Track playback works correctly
- [ ] Sign-out clears cached data

### **API Testing**
Use Postman or similar tools to test Spotify API endpoints with OAuth tokens.

## Troubleshooting

### **Authentication Issues**
- Verify Client ID is correct
- Check redirect URI matches exactly
- Ensure all required scopes are requested
- Clear app data and retry

### **API Errors**
- Check token expiration and refresh
- Verify network connectivity
- Review API rate limits
- Check Spotify API status

### **Playback Issues**
- Ensure Spotify app is installed
- Verify user has active Spotify account
- Check playback permissions

## Resources

- [Spotify Web API Documentation](https://developer.spotify.com/documentation/web-api)
- [Spotify Android SDK](https://developer.spotify.com/documentation/android)
- [OAuth 2.0 PKCE Flow](https://oauth.net/2/pkce/)
- [Material Design 3](https://m3.material.io/)

## License & Attribution

This integration uses the Spotify Web API and complies with Spotify's Developer Terms of Service. All Spotify trademarks and branding are property of Spotify AB.
