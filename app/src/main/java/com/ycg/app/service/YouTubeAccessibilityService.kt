package com.ycg.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ycg.app.data.AllowListMatcher
import com.ycg.app.data.AllowListRepository
import com.ycg.app.overlay.SmallBlockOverlay
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
 *   2. Shows a small "Blocked: <channel>" overlay banner near the top of
 *      the screen with a one-tap "Allow" button.
 *   3. Performs `GLOBAL_ACTION_BACK` to leave the watch page, then attempts
 *      to dismiss the YouTube mini-player by clicking its close button —
 *      so only the disallowed video closes, not YouTube itself.
 *
 * If the user has not granted SYSTEM_ALERT_WINDOW (so we can't show the
 * banner overlay), we fall back to the older [BlockedActivity] flow which
 * does not need that permission.
 *
 * Detection
 * ---------
 * Channel detection lives in [ChannelDetector]. It's intentionally
 * conservative — if it cannot identify the channel of the currently playing
 * video with high confidence, it returns null and we fail open.
 */
class YouTubeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "YTGuard"
        private const val YT_PACKAGE = "com.google.android.youtube"

        private const val MIN_EVAL_INTERVAL_MS = 600L
        private const val POST_BLOCK_COOLDOWN_MS = 2_500L

        @Volatile
        private var instance: YouTubeAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var allowList: AllowListRepository
    private lateinit var overlay: SmallBlockOverlay
    private val allowListState = MutableStateFlow<Set<String>>(emptySet())
    private var collectorJob: Job? = null

    private var lastEvalAt = 0L
    private var lastBlockAt = 0L
    private var lastDecisionChannel: String? = null

    override fun onCreate() {
        super.onCreate()
        allowList = AllowListRepository(applicationContext)
        overlay = SmallBlockOverlay(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        collectorJob = scope.launch {
            allowList.allowedChannels.collect { allowListState.value = it }
        }
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
        overlay.hide()
        super.onDestroy()
    }

    // --------------------------------------------------------------------
    // Decision flow.
    // --------------------------------------------------------------------

    private fun evaluateCurrentWindow() {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val detected = ChannelDetector.detect(root)
        if (detected == null) {
            // Don't know what we're looking at → fail open.
            lastDecisionChannel = null
            return
        }

        if (detected != lastDecisionChannel) {
            Log.i(TAG, "Detected channel='$detected'")
            // Persist for the home screen "last seen" UX.
            scope.launch {
                allowList.setLastDetected(detected, System.currentTimeMillis())
            }
        }
        lastDecisionChannel = detected

        if (AllowListMatcher.isAllowed(detected, allowListState.value)) return

        triggerBlock(detected)
    }

    private fun triggerBlock(channelName: String) {
        lastBlockAt = SystemClock.uptimeMillis()
        Log.i(TAG, "Blocking channel='$channelName'")

        // 1. Cut audio immediately.
        sendMediaPause()

        // 2. Show the small overlay if we have permission; otherwise fall
        //    back to the full-screen blocked-activity flow.
        val hasOverlay = Settings.canDrawOverlays(applicationContext)
        val attached = if (hasOverlay) {
            overlay.show(channelName) { name -> approveChannel(name) }
        } else {
            false
        }

        if (!attached) {
            launchFullScreenFallback(channelName)
            return
        }

        // 3. Close just the disallowed video — not YouTube.
        //    Press BACK once: from a watch page this minimises into the
        //    mini-player. Then a moment later try to find and dismiss the
        //    mini-player so the player goes away entirely. If we're not on
        //    a watch page (e.g. Shorts), the mini-player simply isn't there
        //    and the close-attempt is a harmless no-op.
        performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({ closeMiniPlayerIfAny() }, 350)
        main.postDelayed({ sendMediaPause() }, 600)
        main.postDelayed({ closeMiniPlayerIfAny() }, 1_200)
    }

    private fun launchFullScreenFallback(channelName: String) {
        val intent = Intent(this, BlockedActivity::class.java)
            .putExtra(BlockedActivity.EXTRA_CHANNEL, channelName)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        startActivity(intent)
    }

    private fun approveChannel(channelName: String) {
        scope.launch { allowList.add(channelName) }
        // We just allow-listed it, so don't keep blocking the same video
        // during the cooldown window — extend the cooldown so the user can
        // reopen the video without flicker.
        lastBlockAt = SystemClock.uptimeMillis()
    }

    // --------------------------------------------------------------------
    // YouTube manipulation helpers.
    // --------------------------------------------------------------------

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

    /**
     * After pressing BACK on a watch page, YouTube collapses the player into
     * a mini-player at the bottom of the screen. We try to find its close
     * button and click it so the disallowed video stops entirely without
     * forcing the user out of YouTube.
     *
     * If no mini-player is present this is a harmless no-op.
     */
    private fun closeMiniPlayerIfAny() {
        val root = rootInActiveWindow ?: return
        val candidate = findClickableByContentDescription(root) { desc ->
            desc.equals("Close", ignoreCase = true) ||
                desc.contains("Close player", ignoreCase = true) ||
                desc.contains("Dismiss", ignoreCase = true)
        }
        if (candidate != null) {
            try {
                candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Mini-player close clicked")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to click mini-player close", t)
            }
        }
    }

    private fun findClickableByContentDescription(
        node: AccessibilityNodeInfo,
        match: (String) -> Boolean
    ): AccessibilityNodeInfo? {
        val cd = node.contentDescription?.toString()
        if (!cd.isNullOrEmpty() && node.isClickable && match(cd)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableByContentDescription(child, match)?.let { return it }
        }
        return null
    }
}
