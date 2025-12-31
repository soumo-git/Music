# YouTube Music Player

A modern Android music player application with YouTube integration for browsing and playing videos.

## Setup Instructions

### Authentication

The app uses **OAuth 2.0 with Google Sign-In** for YouTube API access. Users don't need to configure any API keys - they simply sign in with their Google account.

**How it works:**
1. User signs in with their Google account
2. The app receives an OAuth access token
3. All YouTube API requests use this token for authentication
4. No hardcoded API keys needed

### Build and Run

1. Clone the repository
2. Open in Android Studio
3. Build and run the app
4. Users will be prompted to sign in with their Google account on first launch

## Features

- Browse YouTube trending videos
- Search for videos and channels
- View channel information and playlists
- Play videos directly in YouTube app
- Download videos (opens in YouTube)
- Google Sign-In integration
- Dark theme UI

## Architecture

- **MVVM** with ViewBinding
- **Retrofit** for API calls
- **Coroutines** for async operations
- **Firebase** for authentication
- **Material Design 3** components

## Security

- No hardcoded API keys in source code
- OAuth tokens obtained from user's Google account
- Secure authentication with Firebase
- Tokens passed via Authorization header

