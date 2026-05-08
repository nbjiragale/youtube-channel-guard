package com.ycg.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ycg.app.data.AllowListMatcher
import com.ycg.app.data.AllowListRepository
import com.ycg.app.ui.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Watches the YouTube app's accessibility tree, decides whether the currently
 * visible video is from an allow-listed channel, and if not:
 *   1. Dispatches `KEYCODE_MEDIA_PAUSE` to the active media session so audio
 *      stops *immediately*.
 *   2. Launches [BlockedActivity] which becomes the foreground task and
 *      forces YouTube into the background — this is what stops the player UI
 *      and prevents the "playing behind the overlay" problem.
 *
 * Detection strategy
 * ------------------
 * Channel detection is the hardest part because YouTube's release APK uses
 * obfuscated resource IDs and its tree is huge. We use a small set of robust,
 * production-tested signals in priority order:
 *
 *   1. **Subscribe button.** On the watch page YouTube's Subscribe / Subscribed
 *      button is always present and its `contentDescription` is literally of
 *      the form "Subscribe to <ChannelName>" / "Subscribed to <ChannelName>".
 *      This is by far the most reliable per-video channel signal.
 *
 *   2. **Shorts handle.** When the visible UI is a Short (we detect this by
 *      the presence of well-known Shorts-only labels such as the "Shorts"
 *      navigation pip or "Remix" action), we look for the topmost clickable
 *      node whose text starts with "@" — that's the Short's creator handle.
 *
 * If neither signal fires we **fail open** (treat as allowed) rather than
 * risk false-blocking on an arbitrary `@username` from a comment.
 */
class YouTubeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "YTGuard"
        private const val YT_PACKAGE = "com.google.android.youtube"

        /** Don't re-evaluate / re-block more often than this. */
        private const val MIN_EVAL_INTERVAL_MS = 600L

        /** After we trigger a block, ignore further events for this long so we
         *  don't relaunch BlockedActivity in a loop while it's coming up. */
        private const val POST_BLOCK_COOLDOWN_MS = 2_500L

        @Volatile
        private var instance: YouTubeAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var allowList: AllowListRepository
    private val allowListState = MutableStateFlow<Set<String>>(emptySet())
    private var collectorJob: Job? = null

    private var lastEvalAt = 0L
    private var lastBlockAt = 0L
    private var lastDecisionChannel: String? = null

    override fun onCreate() {
        super.onCreate()
        allowList = AllowListRepository(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        collectorJob = scope.launch {
            allowList.allowedChannels.collect { allowListState.value = it }
        }
        // Optional persistent notification.
        startService(Intent(this, GuardForegroundService::class.java))
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName != YT_PACKAGE) return

        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < POST_BLOCK_COOLDOWN_MS) return
        if (now - lastEvalAt < MIN_EVAL_INTERVAL_MS) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                lastEvalAt = now
                evaluateCurrentWindow()
            }
            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        collectorJob?.cancel()
        super.onDestroy()
    }

    // --------------------------------------------------------------------
    // Decision flow.
    // --------------------------------------------------------------------

    private fun evaluateCurrentWindow() {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val detected = ChannelDetector.detect(root)
        if (detected == null) {
            // Don't know what we're looking at — explicitly allow. Better to
            // under-enforce on home/search/library pages than to false-block.
            lastDecisionChannel = null
            return
        }

        // Re-blocking the same channel within the cooldown window is already
        // suppressed above; this is just an info log.
        if (detected != lastDecisionChannel) {
            Log.i(TAG, "Detected channel='$detected'")
        }
        lastDecisionChannel = detected

        if (AllowListMatcher.isAllowed(detected, allowListState.value)) return

        triggerBlock(detected)
    }

    private fun triggerBlock(channelName: String) {
        lastBlockAt = SystemClock.uptimeMillis()
        Log.i(TAG, "Blocking channel='$channelName'")

        // 1. Pause audio first so the user doesn't hear a half-second of the
        //    disallowed video while BlockedActivity is being created.
        sendMediaPause()

        // 2. Launch our blocking screen on top of YouTube. Becoming the
        //    foreground task is what actually backgrounds YouTube and stops
        //    the player UI — the overlay-on-top approach we used before could
        //    not do that.
        val intent = Intent(this, BlockedActivity::class.java)
            .putExtra(BlockedActivity.EXTRA_CHANNEL, channelName)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        startActivity(intent)

        // 3. Belt-and-suspenders: a moment later, send another pause in case
        //    YouTube grabbed audio focus back during the activity transition.
        main.postDelayed({ sendMediaPause() }, 400)
    }

    private fun sendMediaPause() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
        try {
            am.dispatchMediaKeyEvent(down)
            am.dispatchMediaKeyEvent(up)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to dispatch media pause", t)
        }
    }
}
