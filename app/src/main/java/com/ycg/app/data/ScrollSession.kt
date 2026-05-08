package com.ycg.app.data

/**
 * Pure state machine that tracks "scroll count for the current YouTube
 * session" and decides when to fire warnings.
 *
 * Concepts:
 * - **Scroll** = one debounced finger-swipe gesture. Multiple raw scroll
 *   events within [SCROLL_DEBOUNCE_MS] are coalesced into one.
 * - **Session** = one continuous opening of YouTube. Resets when YouTube
 *   has been in the background for at least [SESSION_RESET_AFTER_MS].
 * - **Warning fire** = caller should display the threshold popup. Fires
 *   the first time count crosses each multiple of `threshold` (20, 40,
 *   60…) and only once per crossing.
 * - **Snooze** = user tapped the pill — supress further pill colour
 *   warnings *and* threshold popups for the remainder of this session.
 *   Counting itself continues.
 *
 * Time inputs are caller-supplied (`uptimeMillis`) so the class is pure
 * and unit-testable.
 */
class ScrollSession(
    val threshold: Int = DEFAULT_SCROLL_THRESHOLD
) {
    private var sessionStartedAt: Long = -1
    private var lastForegroundAt: Long = -1
    private var lastScrollAt: Long = -1
    private var snoozed: Boolean = false
    private var lastFiredMilestone: Int = 0
    private var _count: Int = 0

    val count: Int get() = _count
    val isSnoozed: Boolean get() = snoozed

    /**
     * Mark YouTube as being in the foreground at [nowUptimeMs]. If the
     * gap since [lastForegroundAt] is too large, treat it as a brand-new
     * session (zero out count).
     *
     * @return true if a new session was started.
     */
    fun onYouTubeForeground(nowUptimeMs: Long): Boolean {
        val noPriorSession = sessionStartedAt < 0
        val started = if (noPriorSession ||
            nowUptimeMs - lastForegroundAt > SESSION_RESET_AFTER_MS
        ) {
            reset(nowUptimeMs)
            true
        } else {
            false
        }
        lastForegroundAt = nowUptimeMs
        return started
    }

    /**
     * Mark YouTube as having gone to background. Doesn't reset the
     * session immediately (that happens lazily when foreground is
     * detected after [SESSION_RESET_AFTER_MS]).
     */
    fun onYouTubeBackground(nowUptimeMs: Long) {
        lastForegroundAt = nowUptimeMs
    }

    /**
     * Called for every raw TYPE_VIEW_SCROLLED event. Returns a
     * [Decision] saying what the caller should do (update pill, fire
     * threshold popup, both, or nothing).
     */
    fun onRawScrollEvent(nowUptimeMs: Long): Decision {
        val isNewSwipe = nowUptimeMs - lastScrollAt > SCROLL_DEBOUNCE_MS
        lastScrollAt = nowUptimeMs
        if (!isNewSwipe) return Decision.None

        _count += 1

        val crossedMilestone =
            _count >= threshold &&
                _count % threshold == 0 &&
                _count != lastFiredMilestone &&
                !snoozed
        if (crossedMilestone) {
            lastFiredMilestone = _count
            return Decision.WarnAtMilestone(_count)
        }
        return Decision.IncrementOnly(_count)
    }

    fun snooze() {
        snoozed = true
    }

    fun reset(nowUptimeMs: Long) {
        sessionStartedAt = nowUptimeMs
        lastScrollAt = -1
        snoozed = false
        lastFiredMilestone = 0
        _count = 0
    }

    sealed class Decision {
        data object None : Decision()
        data class IncrementOnly(val count: Int) : Decision()
        data class WarnAtMilestone(val count: Int) : Decision()
    }

    companion object {
        const val SCROLL_DEBOUNCE_MS = 300L
        const val SESSION_RESET_AFTER_MS = 30_000L
    }
}
