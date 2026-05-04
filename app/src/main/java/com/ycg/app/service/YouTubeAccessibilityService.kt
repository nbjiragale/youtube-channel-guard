package com.ycg.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ycg.app.data.AllowListMatcher
import com.ycg.app.data.AllowListRepository
import com.ycg.app.overlay.BlockOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Watches the YouTube app's UI tree and decides whether the currently visible
 * video is from an allow-listed channel. If not, it shows a block overlay and
 * navigates back after a short delay.
 *
 * Implementation notes
 * --------------------
 * YouTube's resource IDs change between releases, so instead of hard-coding
 * fragile IDs we walk the visible window's accessibility tree and look for
 * candidate "channel name" nodes by structural heuristics:
 *
 *  - Watch page: under the title there's a horizontal cluster containing the
 *    avatar and channel name. We look for a clickable / focusable node whose
 *    text starts with "@" (handle) or sits adjacent to a node whose
 *    contentDescription contains "channel".
 *  - Shorts: each Short has a "@handle" tappable at the bottom. Same heuristic.
 *
 * Heuristics evolve; if you find a YouTube build where detection misses, the
 * behaviour falls back safely to "no decision" — i.e. we do NOT block what we
 * can't read. False-blocking would be far worse than a missed block.
 */
class YouTubeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "YTGuard"
        private const val YT_PACKAGE = "com.google.android.youtube"
        private const val BLOCK_BACK_DELAY_MS = 4_000L

        @Volatile
        private var instance: YouTubeAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var allowList: AllowListRepository
    private lateinit var overlayManager: BlockOverlayManager

    private val allowListState = MutableStateFlow<Set<String>>(emptySet())
    private var collectorJob: Job? = null

    /**
     * Last channel we saw + acted on, so we don't keep flickering the overlay
     * on every TYPE_WINDOW_CONTENT_CHANGED event.
     */
    private var lastEvaluatedChannel: String? = null
    private var pendingBackRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        allowList = AllowListRepository(applicationContext)
        overlayManager = BlockOverlayManager(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Keep the latest allow-list in memory so we don't hit DataStore on
        // every accessibility event.
        collectorJob = scope.launch {
            allowList.allowedChannels.collect { allowListState.value = it }
        }
        // Start the foreground status notification.
        val intent = Intent(this, GuardForegroundService::class.java)
        startForegroundService(intent)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName != YT_PACKAGE) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                evaluateCurrentWindow()
            }
            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        collectorJob?.cancel()
        cancelPendingBack()
        overlayManager.hide()
        super.onDestroy()
    }

    private fun evaluateCurrentWindow() {
        val root = rootInActiveWindow ?: return
        val channelName = try {
            findChannelName(root)
        } catch (t: Throwable) {
            Log.w(TAG, "Channel detection failed", t)
            null
        }

        if (channelName == null) {
            // We don't know what channel this is — clear stale block UI but do
            // NOT trigger a block. Better to under-enforce than to false-positive
            // on, e.g., the home page where no single channel is visible.
            if (overlayManager.isShowing()) {
                overlayManager.hide()
                cancelPendingBack()
            }
            lastEvaluatedChannel = null
            return
        }

        // Same channel as last decision — nothing to do.
        if (channelName.equals(lastEvaluatedChannel, ignoreCase = true) &&
            overlayManager.isShowing()
        ) return
        lastEvaluatedChannel = channelName

        val allowed = AllowListMatcher.isAllowed(channelName, allowListState.value)
        Log.i(TAG, "Detected channel='$channelName' allowed=$allowed")
        if (allowed) {
            overlayManager.hide()
            cancelPendingBack()
        } else {
            overlayManager.show(channelName)
            scheduleBack()
        }
    }

    private fun scheduleBack() {
        cancelPendingBack()
        val r = Runnable {
            // Press back to leave the watch / shorts page. If still on YouTube
            // and still on a disallowed video, the next event will re-trigger.
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        pendingBackRunnable = r
        mainHandler.postDelayed(r, BLOCK_BACK_DELAY_MS)
    }

    private fun cancelPendingBack() {
        pendingBackRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingBackRunnable = null
    }

    // --------------------------------------------------------------------
    // Channel-name detection heuristics.
    // --------------------------------------------------------------------

    private fun findChannelName(root: AccessibilityNodeInfo): String? {
        // Heuristic 1: look for a node whose viewIdResourceName ends with a
        // known channel-name id. YouTube has historically used ids ending in
        // "/channel_name" or "/channel_title".
        val byId = findFirstByIdSuffix(root, listOf("channel_name", "channel_title", "owner_text"))
        val candidate1 = byId?.let { extractText(it) }?.cleanedChannel()
        if (!candidate1.isNullOrEmpty()) return candidate1

        // Heuristic 2: any text node starting with "@" that is clickable —
        // YouTube uses @handle for the channel link on Shorts and the new
        // watch UI.
        val handle = findHandleNode(root)
        val candidate2 = handle?.cleanedChannel()
        if (!candidate2.isNullOrEmpty()) return candidate2

        // Heuristic 3: a node whose contentDescription is exactly "Channel" —
        // its sibling typically holds the channel text. This is the most
        // brittle, used only as a last resort.
        val sibling = findChannelByDescription(root)
        return sibling?.cleanedChannel()
    }

    private fun findFirstByIdSuffix(
        node: AccessibilityNodeInfo,
        suffixes: List<String>
    ): AccessibilityNodeInfo? {
        val viewId = node.viewIdResourceName
        if (viewId != null && suffixes.any { viewId.endsWith("/$it") || viewId.endsWith(it) }) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findFirstByIdSuffix(child, suffixes)?.let { return it }
        }
        return null
    }

    private fun findHandleNode(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()
        if (!text.isNullOrEmpty() && text.startsWith("@") && text.length in 2..40) {
            return text
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findHandleNode(child)?.let { return it }
        }
        return null
    }

    private fun findChannelByDescription(node: AccessibilityNodeInfo): String? {
        val desc = node.contentDescription?.toString()?.lowercase()
        if (desc != null && (desc == "channel" || desc.contains("channel name"))) {
            return extractText(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findChannelByDescription(child)?.let { return it }
        }
        return null
    }

    private fun extractText(node: AccessibilityNodeInfo): String? {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractText(child)?.let { return it }
        }
        return null
    }

    /**
     * Normalises a raw string from YouTube into a comparable channel name.
     * Strips the leading "@" so that a handle "@MrBeast" matches the
     * user's allow-list entry "MrBeast".
     */
    private fun String?.cleanedChannel(): String? {
        if (this == null) return null
        var s = trim()
        if (s.startsWith("@")) s = s.removePrefix("@")
        // Handle "Channel name • 1.2M subscribers" style strings.
        s = s.substringBefore('•').trim()
        return s.ifBlank { null }
    }
}
