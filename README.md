# YouTube Channel Guard

A personal-use Android app with two complementary features:

1. **Allow-list enforcement.** Runs an Accessibility Service that watches the
   YouTube app and blocks any video whose channel isn't on your allow-list.
   When a disallowed video plays, audio is paused, a small centred modal pops
   up ("Channel not allowed / [Allow] [OK]"), and tapping OK closes only the
   video — YouTube itself stays open.
2. **Curated feed.** A custom feed inside this app showing the latest uploads
   from your allowed channels, fetched via the YouTube Data API v3. Tap a
   video and it opens in YouTube. The idea is to use Channel Guard's feed
   instead of YouTube's own home screen.

The app is intentionally personal-use — you supply your own API key in
`local.properties`.

## Setup

### 1. Get a YouTube Data API v3 key

1. Open https://console.cloud.google.com/, create a project.
2. **APIs & Services → Library**, search "YouTube Data API v3" → **Enable**.
3. **APIs & Services → Credentials → Create credentials → API key**.
4. Copy the key. Optionally restrict it (Application: Android apps, package
   `com.ycg.app`; API: only YouTube Data API v3).

### 2. Drop it into `local.properties`

In the repo root:

```properties
youtube.api.key=AIza...your-key-here...
```

`local.properties` is gitignored (see `.gitignore`) so the key never leaves
your machine. Without a key the app still installs, but the Feed tab will
show "API key not configured" and channels won't have avatars.

### 3. Build

```bash
./gradlew :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install
with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Required Android permissions

The first time you launch the app it walks you through these:

1. **Accessibility access** — `Settings → Accessibility → Channel Guard → On`.
   Required for the blocker to read YouTube's screen.
2. **Display over other apps** — required so the block dialog (with OK / Allow
   buttons) can render on top of YouTube.
3. **Notifications** (Android 13+) — for the persistent "guard active"
   notification.

## How blocking works

Android doesn't let apps inspect each other's state directly, so Channel
Guard uses an Accessibility Service — the only supported way to read another
app's on-screen UI tree.

- A foreground service keeps a status notification visible.
- The accessibility service receives `WINDOW_STATE_CHANGED` /
  `WINDOW_CONTENT_CHANGED` events from `com.google.android.youtube` and
  extracts the current channel name from the Subscribe button, the @handle
  shown in Shorts overlays, etc.
- If the detected channel isn't on the allow-list:
    1. Audio is paused via `KEYCODE_MEDIA_PAUSE`.
    2. A centred modal overlay appears with "Allow" / "OK" buttons.
    3. **OK** → press BACK (collapses watch page to mini-player) → click the
       mini-player's Close button. YouTube stays open on Home / Subs / Shorts.
    4. **Allow** → adds the channel to the allow-list.

Matching is centralised in `AllowListMatcher` and is unit-tested.

## How the feed works

For each allow-listed channel we call:

- `channels.list?forHandle=@…` — resolves the user's input to a canonical
  channel ID, display name, avatar, and uploads-playlist ID.
- `playlistItems.list?playlistId=…&maxResults=10` — most recent uploads.
- `videos.list?id=…` — pulls in durations.

Quota cost per refresh: `~ N + 1` units for `N` allow-listed channels — well
inside the free 10,000-units/day quota.

The feed merges everyone's recent videos, sorts by `publishedAt` descending,
and displays them as cards. Tap a card → opens in YouTube via
`vnd.youtube:VIDEO_ID` (falls back to `https://youtube.com/watch?v=…` if the
YouTube app isn't installed).

## Project layout

```
app/src/main/java/com/ycg/app/
├── GuardApp.kt                       # Application + notification channel
├── data/
│   ├── AllowedChannel.kt             # Persisted channel record
│   ├── AllowListRepository.kt        # DataStore-backed list of AllowedChannel
│   ├── AllowListMatcher.kt           # Pure matching logic (unit-tested)
│   ├── ChannelResolver.kt            # User input → AllowedChannel via the API
│   ├── FeedRepository.kt             # Aggregates uploads across allow-list
│   ├── Format.kt                     # Duration + relative-time formatting
│   └── api/
│       ├── YouTubeApi.kt             # Tiny OkHttp client around Data API v3
│       └── YouTubeApiDto.kt          # @Serializable response shapes
├── service/
│   ├── YouTubeAccessibilityService.kt   # Detection + decision loop
│   ├── GuardForegroundService.kt        # Persistent status notification
│   └── BootReceiver.kt                  # Re-start notification after reboot
├── overlay/
│   └── SmallBlockOverlay.kt             # Centred-modal block UI
└── ui/
    ├── MainActivity.kt
    ├── HomeViewModel.kt
    ├── FeedViewModel.kt
    └── screens/
        ├── MainScreen.kt              # Bottom-nav (Feed | Channels)
        ├── FeedScreen.kt              # Curated feed
        └── HomeScreen.kt              # Allow-list management
```

## Limitations / honest tradeoffs

- **YouTube app only.** YouTube in a browser isn't covered.
- **Allow-list matching is heuristic on the blocker side.** Channel display
  names can collide. Once a channel is resolved via the API we also store its
  canonical channel ID, but the on-screen detection still operates on names.
- **No bypass-resistance.** Anyone with Settings access can disable the
  accessibility service. This is a self-discipline aid, not a kiosk lockdown.
- **Feed shows only public uploads.** Members-only / private videos won't
  appear. The API can't see your subscriptions without OAuth, which this app
  intentionally doesn't use.
