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
import com.ycg.app.data.RestrictionsRepository
import com.ycg.app.data.ScrollSession
import com.ycg.app.overlay.FloatingCounterOverlay
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
        private const val PILL_INACTIVITY_HIDE_MS = 5_000L

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
    private lateinit var restrictions: RestrictionsRepository
    private lateinit var overlay: SmallBlockOverlay
    private lateinit var counterOverlay: FloatingCounterOverlay
    private val allowListState = MutableStateFlow<Set<String>>(emptySet())
    private val lockdownState = MutableStateFlow<List<LockdownWindow>>(emptyList())
    private val blockShortsState = MutableStateFlow(false)
    private val scrollEnabledState = MutableStateFlow(true)
    private val scrollThresholdState = MutableStateFlow(20)
    private var collectorJob: Job? = null
    private var lockdownCollectorJob: Job? = null
    private var restrictionsCollectorJob: Job? = null
    private var scrollPrefsCollectorJob: Job? = null

    private var lastEvalAt = 0L
    private var lastBlockAt = 0L
    private var lastDecisionChannel: String? = null
    private var overlayUp = false

    private var scrollSession = ScrollSession(threshold = 20)
    private val hidePillRunnable = Runnable { counterOverlay.hide() }

    override fun onCreate() {
        super.onCreate()
        allowList = AllowListRepository(applicationContext)
        lockdown = LockdownRepository(applicationContext)
        restrictions = RestrictionsRepository(applicationContext)
        overlay = SmallBlockOverlay(applicationContext)
        counterOverlay = FloatingCounterOverlay(applicationContext)
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
        restrictionsCollectorJob = scope.launch {
            restrictions.blockShorts.collect { blockShortsState.value = it }
        }
        scrollPrefsCollectorJob = scope.launch {
            launch {
                restrictions.scrollCounterEnabled.collect { enabled ->
                    scrollEnabledState.value = enabled
                    if (!enabled) main.post { counterOverlay.hide() }
                }
            }
            launch {
                restrictions.scrollCounterThreshold.collect { value ->
                    scrollThresholdState.value = value
                    scrollSession = ScrollSession(threshold = value).also { fresh ->
                        // Preserve current foreground stamp so we don't
                        // immediately reset.
                        fresh.onYouTubeForeground(SystemClock.uptimeMillis())
                    }
                }
            }
        }
        startService(Intent(this, GuardForegroundService::class.java))
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName != YT_PACKAGE) return

        val now = SystemClock.uptimeMillis()

        // Mark YouTube as foreground; lazy-reset the scroll session if
        // we've been away long enough. Do this on EVERY YouTube event so
        // the inactivity-watchdog also keeps the pill on screen.
        onYouTubeEventSeen(now)

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleScrollEvent(now)
                return
            }
        }

        // Block-decision flow is throttled / suppressed while our modal
        // is up, but scroll counting (above) is not.
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

    // --------------------------------------------------------------------
    // Scroll counter.
    // --------------------------------------------------------------------

    private fun onYouTubeEventSeen(nowUptimeMs: Long) {
        val isNewSession = scrollSession.onYouTubeForeground(nowUptimeMs)
        if (!scrollEnabledState.value) {
            main.removeCallbacks(hidePillRunnable)
            counterOverlay.hide()
            return
        }
        // Show / refresh the pill if overlay permission is granted.
        if (Settings.canDrawOverlays(applicationContext)) {
            if (!counterOverlay.isShowing()) {
                counterOverlay.show(
                    initialCount = scrollSession.count,
                    threshold = scrollSession.threshold,
                    onTap = { snoozePillForSession() }
                )
            } else if (isNewSession) {
                counterOverlay.update(
                    count = scrollSession.count,
                    threshold = scrollSession.threshold
                )
            }
        }
        // Keep the pill alive for PILL_INACTIVITY_HIDE_MS after the most
        // recent YouTube event; if YouTube goes to background, the pill
        // disappears on its own.
        main.removeCallbacks(hidePillRunnable)
        main.postDelayed(hidePillRunnable, PILL_INACTIVITY_HIDE_MS)
    }

    private fun handleScrollEvent(nowUptimeMs: Long) {
        if (!scrollEnabledState.value) return
        val decision = scrollSession.onRawScrollEvent(nowUptimeMs)
        when (decision) {
            ScrollSession.Decision.None -> Unit
            is ScrollSession.Decision.IncrementOnly -> {
                counterOverlay.update(decision.count, scrollSession.threshold)
            }
            is ScrollSession.Decision.WarnAtMilestone -> {
                counterOverlay.update(decision.count, scrollSession.threshold)
                triggerScrollWarning(decision.count)
            }
        }
    }

    private fun snoozePillForSession() {
        scrollSession.snooze()
        // Visually flatten the pill back to neutral so the user gets
        // feedback that the snooze took effect.
        counterOverlay.update(
            count = scrollSession.count,
            threshold = Int.MAX_VALUE
        )
    }

    private fun triggerScrollWarning(count: Int) {
        // The warning popup is non-blocking — it doesn't pause media or
        // close the video. It's a nudge.
        if (!Settings.canDrawOverlays(applicationContext)) {
            showToast("You've scrolled $count times this session.")
            return
        }
        val attached = overlay.showScrollWarning(
            title = "You've scrolled $count times.",
            subtitle = scrollNudgeMessage(count),
            onDismiss = {
                overlayUp = false
                scrollSession.snooze()
                counterOverlay.update(
                    count = scrollSession.count,
                    threshold = Int.MAX_VALUE
                )
            },
            onClose = {
                overlayUp = false
                scrollSession.snooze()
                counterOverlay.hide()
                closeDisallowedVideo()
            }
        )
        if (attached) {
            overlayUp = true
        }
    }

    private fun scrollNudgeMessage(count: Int): String = when {
        count >= 60 -> "An hour of your life can disappear here. " +
            "Maybe step away for a bit?"
        count >= 40 -> "Still here? You've scrolled $count times. " +
            "Probably nothing new worth your time tonight."
        else -> "Maybe close YouTube for a bit?"
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        collectorJob?.cancel()
        lockdownCollectorJob?.cancel()
        restrictionsCollectorJob?.cancel()
        scrollPrefsCollectorJob?.cancel()
        main.removeCallbacks(hidePillRunnable)
        overlay.hide()
        counterOverlay.hide()
        overlayUp = false
        super.onDestroy()
    }

    // --------------------------------------------------------------------
    // Decision flow.
    // --------------------------------------------------------------------

    private fun evaluateCurrentWindow() {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return

        // Block-Shorts rule runs first and short-circuits everything else.
        // Detection is structural (Shorts-specific buttons in the tree) so
        // we don't need to know the channel at all to decide here.
        if (blockShortsState.value && ChannelDetector.isShorts(root)) {
            triggerShortsBlock()
            return
        }

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

    private fun triggerShortsBlock() {
        lastBlockAt = SystemClock.uptimeMillis()
        Log.i(TAG, "Shorts blocked")

        sendMediaPause()

        val canOverlay = Settings.canDrawOverlays(applicationContext)
        if (canOverlay) {
            val attached = overlay.showLockdown(
                title = "Shorts are blocked",
                subtitle = "Tap OK to close",
                onOk = { closeDisallowedVideo() }
            )
            if (attached) {
                overlayUp = true
                return
            }
        }

        showToast("Shorts are blocked")
        closeDisallowedVideo()
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
     *   2. **Only if a player is actually visible**: `GLOBAL_ACTION_BACK`
     *      to collapse the watch page to mini-player, or to exit a Short.
     *      Skipping BACK when no player is open is critical — pressing
     *      BACK from YouTube's home / feed would exit the YouTube app
     *      entirely, which is *not* what we want.
     *   3. After a beat, **only if a mini-player is detected**, click its
     *      Close button so the player goes away entirely. Skipping this
     *      when there's no mini-player avoids accidentally clicking other
     *      "Close"-labelled buttons elsewhere in YouTube's UI.
     */
    private fun closeDisallowedVideo() {
        overlayUp = false
        sendMediaPause()

        val root = rootInActiveWindow
        val playerOpen = root != null && ChannelDetector.isPlayerOpen(root)

        if (playerOpen) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } else {
            Log.i(TAG, "closeDisallowedVideo: no player visible, skipping BACK")
        }

        // Re-pause audio in case YouTube grabbed focus back, and try
        // to dismiss any mini-player that materialised after BACK.
        main.postDelayed({
            sendMediaPause()
            closeMiniPlayerIfAny()
        }, 350)
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
        // Be strict on what we're willing to click. "Close" alone matches
        // a lot of things in YouTube's UI (close cast dialog, close
        // search overlay, close survey card, …) so we only click nodes
        // whose content description clearly identifies the mini-player.
        val candidate = findClickableByContentDescription(root) { desc ->
            val d = desc.lowercase()
            d == "close mini player" ||
                d == "close miniplayer" ||
                d == "close the mini player" ||
                d == "close player" ||
                d == "dismiss mini player" ||
                d == "dismiss miniplayer"
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
