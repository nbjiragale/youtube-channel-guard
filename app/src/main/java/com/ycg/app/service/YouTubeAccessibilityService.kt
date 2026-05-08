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
import android.widget.Toast
import com.ycg.app.data.AllowListMatcher
import com.ycg.app.data.AllowListRepository
import com.ycg.app.data.LockdownEngine
import com.ycg.app.data.LockdownRepository
import com.ycg.app.data.LockdownWindow
import com.ycg.app.overlay.SmallBlockOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Watches the YouTube app's accessibility tree, decides whether the currently
 * visible video is from an allow-listed channel, and if not blocks it.
 *
 * Block flow (overlay permission granted)
 * ---------------------------------------
 *   1. Pause audio immediately via `KEYCODE_MEDIA_PAUSE`.
 *   2. Show a small centred modal overlay: "Channel not allowed / @X /
 *      [Allow] [OK]".
 *   3. Wait for the user.
 *      - **OK** → close just the disallowed video: press BACK (which
 *        collapses the watch page into YouTube's mini-player), then click
 *        the mini-player's close button. YouTube itself stays open.
 *      - **Allow** → add channel to allow-list, hide overlay. Video can be
 *        resumed by the user.
 *
 * Block flow (overlay permission NOT granted — fallback)
 * ------------------------------------------------------
 *   1. Pause audio.
 *   2. Show a Toast "Channel not allowed: <X>".
 *   3. Silently close the disallowed video the same way OK does.
 *
 * Either way YouTube is never forcibly exited.
 */
class YouTubeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "YTGuard"
        private const val YT_PACKAGE = "com.google.android.youtube"

        private const val MIN_EVAL_INTERVAL_MS = 600L
        private const val POST_BLOCK_COOLDOWN_MS = 2_500L

        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("h:mm a")

        @Volatile
        private var instance: YouTubeAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var allowList: AllowListRepository
    private lateinit var lockdown: LockdownRepository
    private lateinit var overlay: SmallBlockOverlay
    private val allowListState = MutableStateFlow<Set<String>>(emptySet())
    private val lockdownState = MutableStateFlow<List<LockdownWindow>>(emptyList())
    private var collectorJob: Job? = null
    private var lockdownCollectorJob: Job? = null

    private var lastEvalAt = 0L
    private var lastBlockAt = 0L
    private var lastDecisionChannel: String? = null
    private var overlayUp = false

    override fun onCreate() {
        super.onCreate()
        allowList = AllowListRepository(applicationContext)
        lockdown = LockdownRepository(applicationContext)
        overlay = SmallBlockOverlay(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        collectorJob = scope.launch {
            allowList.allowedNames.collect { allowListState.value = it }
        }
        lockdownCollectorJob = scope.launch {
            lockdown.windows.collect { lockdownState.value = it }
        }
        startService(Intent(this, GuardForegroundService::class.java))
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName != YT_PACKAGE) return

        val now = SystemClock.uptimeMillis()
        // Don't re-trigger while our overlay is up — user is still deciding.
        if (overlayUp) return
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
        lockdownCollectorJob?.cancel()
        overlay.hide()
        overlayUp = false
        super.onDestroy()
    }

    // --------------------------------------------------------------------
    // Decision flow.
    // --------------------------------------------------------------------

    private fun evaluateCurrentWindow() {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val detected = ChannelDetector.detect(root)
        if (detected == null) {
            lastDecisionChannel = null
            return
        }

        if (detected != lastDecisionChannel) {
            Log.i(TAG, "Detected channel='$detected'")
            scope.launch {
                allowList.setLastDetected(detected, System.currentTimeMillis())
            }
        }
        lastDecisionChannel = detected

        // Lockdown takes precedence over the allow-list — when inside a
        // window, EVERY video is blocked, even from allowed channels.
        val activeLockdown =
            LockdownEngine.activeWindow(LocalDateTime.now(), lockdownState.value)
        if (activeLockdown != null) {
            triggerLockdownBlock(activeLockdown)
            return
        }

        if (AllowListMatcher.isAllowed(detected, allowListState.value)) return

        triggerBlock(detected)
    }

    private fun triggerLockdownBlock(window: LockdownWindow) {
        lastBlockAt = SystemClock.uptimeMillis()
        Log.i(TAG, "Lockdown active — blocking. label='${window.label}'")

        sendMediaPause()

        val canOverlay = Settings.canDrawOverlays(applicationContext)
        if (canOverlay) {
            val now = LocalDateTime.now()
            val labelText = window.label.ifBlank {
                if (window.crossesMidnight) "Sleep" else "Lockdown"
            }
            val endTime = window.endAt(now).toLocalTime()
            val subtitle = "$labelText until ${TIME_FORMAT.format(endTime)}"
            val attached = overlay.showLockdown(
                title = "Lockdown active",
                subtitle = subtitle,
                onOk = { closeDisallowedVideo() }
            )
            if (attached) {
                overlayUp = true
                return
            }
        }

        showToast("Lockdown active — closing video")
        closeDisallowedVideo()
    }

    private fun triggerBlock(channelName: String) {
        lastBlockAt = SystemClock.uptimeMillis()
        Log.i(TAG, "Blocking channel='$channelName'")

        // Cut audio immediately.
        sendMediaPause()

        val canOverlay = Settings.canDrawOverlays(applicationContext)
        if (canOverlay) {
            val attached = overlay.show(
                channelName = channelName,
                onAllow = { name -> approveChannel(name) },
                onOk = { _ -> closeDisallowedVideo() }
            )
            if (attached) {
                overlayUp = true
                return
            }
        }

        // Permission missing or attach failed — silent fallback.
        showToast("Channel not allowed: $channelName")
        closeDisallowedVideo()
    }

    private fun approveChannel(channelName: String) {
        scope.launch { allowList.addRaw(channelName) }
        overlayUp = false
        // Extend cooldown so we don't re-block before they hit play again.
        lastBlockAt = SystemClock.uptimeMillis()
    }

    /**
     * Closes the currently playing watch page / Short and returns the user
     * to wherever they were inside YouTube — but doesn't leave YouTube
     * itself.
     *
     * Strategy:
     *   1. Pause audio again (belt-and-suspenders for cases where YouTube
     *      reclaimed audio focus while the user was looking at the modal).
     *   2. `GLOBAL_ACTION_BACK` — collapses the watch page to mini-player,
     *      or exits a Short.
     *   3. After a beat, look for the mini-player's "Close" button in the
     *      accessibility tree and click it so the player goes away
     *      entirely. Repeated once a moment later in case the first attempt
     *      ran before the mini-player had finished animating in.
     */
    private fun closeDisallowedVideo() {
        overlayUp = false
        sendMediaPause()
        performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({ closeMiniPlayerIfAny(); sendMediaPause() }, 350)
        main.postDelayed({ closeMiniPlayerIfAny() }, 1_100)
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

    private fun showToast(text: String) {
        main.post {
            try {
                Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) { /* best-effort */ }
        }
    }
}
