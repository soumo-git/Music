# Navigation Structure Guide

## Bottom Navigation Bar (5 Tabs)

```
┌─────────────────────────────────────────────────────────────┐
│  🎵 My Music  │  💑 Duo  │  ⬇️ Downloads  │  🌐 Browse  │  👤 Profile  │
└─────────────────────────────────────────────────────────────┘
```

---

## Tab 1: My Music 🎵
**Status**: ✅ Fully Implemented

### Content
- Songs tab
- Videos tab
- Artists tab
- Albums tab
- Folders tab

### Features
- Search bar for local content
- Sort options
- Shuffle playback
- Play queue management

---

## Tab 2: Duo 💑
**Status**: 🚧 Coming Soon

### Planned Features
- Share music with partner
- Sync playlists
- Shared listening sessions
- Couple's music stats

### Current State
- Placeholder screen with couple's icon
- "Coming soon" message
- Ready for implementation

---

## Tab 3: Downloads ⬇️
**Status**: ✅ Fully Implemented

### Top Bar
```
┌──────────────────────────────────────────────────┐
│  Downloads   [Paste link here..] [Extract 📥]    │
└──────────────────────────────────────────────────┘
```

### Features
- Paste video/playlist URLs
- Extract content from:
  - YouTube
  - Dailymotion
  - Instagram
  - Other supported platforms
- Download video + audio or audio only
- View downloaded content
- Play downloaded media

### Key Changes
- **Moved** from Browse dropdown
- **No dropdown icon** in header
- **Direct access** from bottom nav
- **Same functionality** as before

---

## Tab 4: Browse 🌐
**Status**: ✅ Fully Implemented

### Platform Selector
```
┌────────────────────────────────────────┐
│  [YouTube ▼]  [Search...]              │
└────────────────────────────────────────┘
```

### Platforms
1. **YouTube**
   - Home (Trending, Recommended)
   - Shorts
   - Subscriptions
   - Playlists
   - Profile
   - Search

2. **Spotify** (Integration ready)
   - Home (Featured, New Releases)
   - Your Library
   - Profile
   - Search

### Features
- Platform switching via dropdown
- OAuth authentication
- Search within platform
- Play content through original player

### Key Changes
- **Removed** Downloads option
- **Only** streaming platforms
- **Cleaner** dropdown menu

---

## Tab 5: Profile 👤
**Status**: 🚧 Coming Soon

### Planned Features
- User account management
- Preferences and settings
- Listening history
- Music stats and insights
- Social features

### Current State
- Placeholder screen with profile icon
- "Coming soon" message
- Ready for implementation

---

## Navigation Flow

### From Navigation Drawer
```
☰ Menu
├── Downloads → Opens Downloads tab
├── Settings → Opens Settings activity
├── Equalizer → Opens Equalizer activity
├── User Agreement → Opens legal document
└── Privacy Policy → Opens legal document
```

### Tab Switching
```
User taps tab
    ↓
Hide all other containers
    ↓
Show selected tab container
    ↓
Load fragment if not already loaded
    ↓
Display content
```

---

## Container Management

### MainActivity Containers
```kotlin
- topBar + tabLayout + viewPager     // My Music
- duoContainer                        // Duo
- downloadsTabContainer               // Downloads
- browseContainer                     // Browse
- profileContainer                    // Profile
- playerBarContainer                  // Player (visible across all tabs)
```

### Visibility Logic
```kotlin
when (selectedTab) {
    MY_MUSIC -> show(topBar, tabLayout, viewPager)
    DUO -> show(duoContainer)
    DOWNLOADS -> show(downloadsTabContainer)
    BROWSE -> show(browseContainer)
    PROFILE -> show(profileContainer)
}
// Player bar always visible when playing
```

---

## User Experience

### Quick Access
- **My Music**: Local content, one tap away
- **Duo**: Future couple's feature
- **Downloads**: Direct download access
- **Browse**: Stream from YouTube/Spotify
- **Profile**: User settings and stats

### Consistent Elements
- Bottom navigation always visible
- Player bar appears when playing
- Smooth transitions between tabs
- No data loss on tab switching

---

## Developer Notes

### Adding New Platforms to Browse
```kotlin
// 1. Add to StreamingPlatform enum
enum class StreamingPlatform {
    YOUTUBE,
    SPOTIFY,
    NEW_PLATFORM  // Add here
}

// 2. Create fragment
class NewPlatformFragment : Fragment()

// 3. Update BrowseFragment
when (platform) {
    NEW_PLATFORM -> showNewPlatform()
}
```

### Adding Features to Duo/Profile
```kotlin
// Replace placeholder fragments
class DuoFragment : Fragment() {
    // Implement Duo features
}

class ProfileFragment : Fragment() {
    // Implement Profile features
}
```

---

## Testing Scenarios

### Navigation
- [ ] Tap each tab, verify correct content shows
- [ ] Switch between tabs rapidly
- [ ] Player bar persists across tabs
- [ ] Back button behavior correct

### Downloads Tab
- [ ] Paste link and extract
- [ ] View downloaded content
- [ ] Play downloaded media
- [ ] No dropdown icon in header

### Browse Tab
- [ ] Switch between YouTube/Spotify
- [ ] Search works for each platform
- [ ] No Downloads option in dropdown
- [ ] Authentication flows work

### Placeholders
- [ ] Duo shows coming soon message
- [ ] Profile shows coming soon message
- [ ] Icons display correctly

---

**Last Updated**: December 29, 2025
**Version**: 2.0 (5-Tab Navigation)
