# YouTube Channel Guard

An Android app that runs continuously in the background and only lets you watch
YouTube videos from channels you have explicitly added to an allow-list. If you
open any other channel's video — including autoplaying feed videos and Shorts —
the app shows a full-screen overlay and then automatically presses **back** a
few seconds later to take you off that video.

## How it works

Android does not allow one app to inspect another app's internal state, so
Channel Guard uses an **Accessibility Service** — the only supported way to
read another app's on-screen UI tree.

- A foreground service keeps a persistent status notification visible.
- The accessibility service receives `WINDOW_STATE_CHANGED` /
  `WINDOW_CONTENT_CHANGED` events from `com.google.android.youtube`.
- For each event it walks the visible window's accessibility tree and
  heuristically extracts the channel name (resource-id suffix match → `@handle`
  text match → `contentDescription="channel"` sibling lookup).
- If the detected channel is not on your allow-list (case-insensitive exact
  match), the app shows a full-screen overlay window
  (`TYPE_APPLICATION_OVERLAY`) and schedules a `GLOBAL_ACTION_BACK` four
  seconds later.

The matching logic is centralised in `AllowListMatcher` so it is unit-tested in
isolation.

## Required permissions

The app needs three things from the user — Android does not let apps grant any
of them programmatically:

1. **Accessibility access** — `Settings → Accessibility → YouTube Channel Guard
   → On`. Without this the service receives no events.
2. **Display over other apps** — `Settings → Apps → Special access → Display
   over other apps → Channel Guard → Allow`. Without this the overlay can't be
   drawn (the back action will still fire).
3. **Notifications** (Android 13+) — to keep the foreground service notification
   visible.

The home screen has shortcut buttons to each settings page.

## Building

```bash
./gradlew :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install via
`adb install -r app/build/outputs/apk/debug/app-debug.apk` or just open the
project in Android Studio and click **Run**.

CI (`.github/workflows/android.yml`) runs `lint`, unit tests, and
`assembleDebug` on every PR and uploads the resulting APK as a build artifact.

## Limitations / honest tradeoffs

- **YouTube app only.** YouTube in a browser is *not* covered by this build.
- **Channel-name detection is heuristic.** YouTube's UI changes regularly. If a
  release moves the channel-name view out of the heuristics in
  `YouTubeAccessibilityService.findChannelName`, the app will fail open (i.e.
  not block) rather than false-block.
- **Allow-list matches by name, not channel ID.** Two different channels with
  the same display name would both be allowed. There is no public API for
  fetching the channel ID from the on-screen UI.
- **No bypass-resistance.** A user who can change Settings can disable the
  accessibility service. This app is a self-discipline / parental-control aid,
  not a kiosk lockdown.

## Project layout

```
app/src/main/java/com/ycg/app/
├── GuardApp.kt                       # Application + notification channel
├── data/
│   ├── AllowListRepository.kt        # DataStore-backed Set<String>
│   └── AllowListMatcher.kt           # Pure matching logic (unit-tested)
├── service/
│   ├── YouTubeAccessibilityService.kt   # Detection + decision loop
│   ├── GuardForegroundService.kt        # Persistent status notification
│   └── BootReceiver.kt                  # Re-start notification after reboot
├── overlay/
│   └── BlockOverlayManager.kt           # Full-screen "blocked" overlay
└── ui/
    ├── MainActivity.kt
    ├── HomeViewModel.kt
    └── screens/HomeScreen.kt           # Compose UI for managing the allow-list
```
