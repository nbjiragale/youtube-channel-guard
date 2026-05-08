package com.ycg.app.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Pure-ish channel-name extraction logic, kept out of the AccessibilityService
 * so it can be reasoned about (and one day unit-tested with a fake node tree)
 * in isolation.
 *
 * Returns the detected channel name as displayed by YouTube, or null if we
 * cannot confidently identify the channel of the *currently playing* video.
 *
 * It is deliberately conservative: we'd rather return null and let the guard
 * fail open than hand back a wrong name that gets a video false-blocked.
 */
object ChannelDetector {

    private val SUBSCRIBE_PREFIXES = listOf(
        "Subscribe to ",
        "Subscribed to ",
        "Unsubscribe from ",
        "Unsubscribe ",
        "subscribe to ",
        "subscribed to "
    )

    fun detect(root: AccessibilityNodeInfo): String? {
        // 1. Watch page: the Subscribe button's contentDescription is the
        //    most reliable channel signal in the entire YouTube UI.
        watchPageChannelFromSubscribe(root)?.let { return it.normaliseWhitespace() }

        // 2. Shorts: only run this if we look like we're inside a Short, so
        //    we don't accidentally pick up @handles from comments on a watch
        //    page.
        if (looksLikeShorts(root)) {
            shortsHandle(root)?.let { return it.normaliseWhitespace() }
        }

        return null
    }

    // -------------------------------------------------------------------
    // Watch page.
    // -------------------------------------------------------------------

    private fun watchPageChannelFromSubscribe(node: AccessibilityNodeInfo): String? {
        if (isInsideComments(node)) return null

        val cd = node.contentDescription?.toString()
        if (!cd.isNullOrEmpty()) {
            for (prefix in SUBSCRIBE_PREFIXES) {
                if (cd.startsWith(prefix, ignoreCase = true)) {
                    val raw = cd.substring(prefix.length).trim()
                    val cleaned = stripTrailingPeriod(raw)
                    if (cleaned.isNotEmpty() && cleaned.length <= 100) return cleaned
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            watchPageChannelFromSubscribe(child)?.let { return it }
        }
        return null
    }

    // -------------------------------------------------------------------
    // Shorts.
    // -------------------------------------------------------------------

    /**
     * Heuristic: a Shorts player has its own characteristic action buttons
     * ("Remix", "Dislike this short", etc.) somewhere in the visible window.
     */
    private fun looksLikeShorts(root: AccessibilityNodeInfo): Boolean =
        treeContains(root) { node ->
            val cd = node.contentDescription?.toString()?.lowercase()
            val txt = node.text?.toString()?.lowercase()
            (cd != null && (cd.contains("remix this short") ||
                cd.contains("dislike this short") ||
                cd.contains("like this short") ||
                cd.contains("share this short"))) ||
                txt == "shorts"
        }

    private fun shortsHandle(root: AccessibilityNodeInfo): String? {
        // Find the topmost clickable @handle that isn't inside a comments
        // sheet. Shorts comments open as their own sheet on top, so when
        // they're up we should refuse to decide (the caller handles null
        // by failing open).
        if (treeContains(root) { node ->
            node.text?.toString()?.equals("Comments", ignoreCase = true) == true
        }) {
            return null
        }
        return findHandle(root)
    }

    private fun findHandle(node: AccessibilityNodeInfo): String? {
        if (isInsideComments(node)) return null

        val txt = node.text?.toString()
        if (!txt.isNullOrEmpty() && txt.startsWith("@") && txt.length in 2..40) {
            return txt
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findHandle(child)?.let { return it }
        }
        return null
    }

    // -------------------------------------------------------------------
    // Helpers.
    // -------------------------------------------------------------------

    private fun isInsideComments(node: AccessibilityNodeInfo): Boolean {
        // Walk *up* from the node to see if it's a descendant of the
        // comments subtree. We only check a few levels — YouTube nests deeply
        // and going all the way to root is wasteful.
        var cursor: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (cursor != null && depth < 8) {
            val id = cursor.viewIdResourceName?.lowercase()
            if (id != null && (id.contains("comment") || id.contains("reply"))) {
                return true
            }
            cursor = cursor.parent
            depth++
        }
        return false
    }

    private fun treeContains(
        node: AccessibilityNodeInfo,
        match: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        if (match(node)) return true
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            if (treeContains(c, match)) return true
        }
        return false
    }

    private fun stripTrailingPeriod(s: String): String =
        if (s.endsWith(".")) s.dropLast(1).trim() else s

    private fun String.normaliseWhitespace(): String =
        trim().replace(Regex("\\s+"), " ")
}
