package com.ycg.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSessionTest {

    @Test
    fun `first foreground starts a session`() {
        val s = ScrollSession(threshold = 20)
        val started = s.onYouTubeForeground(0L)
        assertTrue(started)
        assertEquals(0, s.count)
    }

    @Test
    fun `second foreground within reset window does not restart`() {
        val s = ScrollSession(threshold = 20)
        s.onYouTubeForeground(0L)
        s.onRawScrollEvent(1_000L)
        val started = s.onYouTubeForeground(2_000L)
        assertFalse(started)
        assertEquals(1, s.count)
    }

    @Test
    fun `foreground after long gap restarts session`() {
        val s = ScrollSession(threshold = 20)
        s.onYouTubeForeground(0L)
        s.onRawScrollEvent(1_000L)
        s.onRawScrollEvent(2_000L)
        assertEquals(2, s.count)
        // 60s gap > 30s reset window.
        val started = s.onYouTubeForeground(62_000L)
        assertTrue(started)
        assertEquals(0, s.count)
    }

    @Test
    fun `multiple raw events within debounce count as one scroll`() {
        val s = ScrollSession(threshold = 20)
        s.onYouTubeForeground(0L)
        // First gesture.
        s.onRawScrollEvent(1_000L)
        s.onRawScrollEvent(1_050L)
        s.onRawScrollEvent(1_100L)
        s.onRawScrollEvent(1_200L)
        assertEquals(1, s.count)
        // Second gesture, well after debounce.
        s.onRawScrollEvent(2_000L)
        assertEquals(2, s.count)
    }

    @Test
    fun `debounce boundary - exactly at threshold counts as same gesture`() {
        val s = ScrollSession(threshold = 20)
        s.onYouTubeForeground(0L)
        s.onRawScrollEvent(1_000L)
        // 300ms after — boundary, treated as same gesture (delta == DEBOUNCE).
        s.onRawScrollEvent(1_300L)
        assertEquals(1, s.count)
        // 301ms after the previous one — new gesture.
        s.onRawScrollEvent(1_601L)
        assertEquals(2, s.count)
    }

    @Test
    fun `none decision when raw event is debounced`() {
        val s = ScrollSession(threshold = 20)
        s.onYouTubeForeground(0L)
        val first = s.onRawScrollEvent(1_000L)
        val coalesced = s.onRawScrollEvent(1_050L)
        assertTrue(first is ScrollSession.Decision.IncrementOnly)
        assertTrue(coalesced is ScrollSession.Decision.None)
    }

    @Test
    fun `warn fires exactly at threshold and not before`() {
        val s = ScrollSession(threshold = 5)
        s.onYouTubeForeground(0L)
        var t = 1_000L
        val decisions = mutableListOf<ScrollSession.Decision>()
        repeat(5) {
            decisions += s.onRawScrollEvent(t)
            t += 500L
        }
        // First 4 should be IncrementOnly, 5th should be WarnAtMilestone(5).
        assertTrue(decisions[0] is ScrollSession.Decision.IncrementOnly)
        assertTrue(decisions[3] is ScrollSession.Decision.IncrementOnly)
        val last = decisions[4]
        assertTrue(last is ScrollSession.Decision.WarnAtMilestone)
        last as ScrollSession.Decision.WarnAtMilestone
        assertEquals(5, last.count)
    }

    @Test
    fun `warn fires again at next multiple of threshold`() {
        val s = ScrollSession(threshold = 5)
        s.onYouTubeForeground(0L)
        var t = 1_000L
        val warnsAt = mutableListOf<Int>()
        repeat(15) {
            val d = s.onRawScrollEvent(t)
            if (d is ScrollSession.Decision.WarnAtMilestone) warnsAt.add(d.count)
            t += 500L
        }
        assertEquals(listOf(5, 10, 15), warnsAt)
    }

    @Test
    fun `snooze suppresses further warnings until reset`() {
        val s = ScrollSession(threshold = 5)
        s.onYouTubeForeground(0L)
        var t = 1_000L
        repeat(5) { s.onRawScrollEvent(t); t += 500L }
        s.snooze()
        val warnsAfterSnooze = mutableListOf<Int>()
        repeat(20) {
            val d = s.onRawScrollEvent(t)
            if (d is ScrollSession.Decision.WarnAtMilestone) warnsAfterSnooze.add(d.count)
            t += 500L
        }
        assertEquals(emptyList<Int>(), warnsAfterSnooze)
    }

    @Test
    fun `reset clears count and snooze`() {
        val s = ScrollSession(threshold = 5)
        s.onYouTubeForeground(0L)
        var t = 1_000L
        repeat(5) { s.onRawScrollEvent(t); t += 500L }
        s.snooze()
        s.reset(t)
        assertEquals(0, s.count)
        assertFalse(s.isSnoozed)
        // After reset, warn fires again at next threshold crossing.
        val warns = mutableListOf<Int>()
        repeat(5) {
            val d = s.onRawScrollEvent(t)
            if (d is ScrollSession.Decision.WarnAtMilestone) warns.add(d.count)
            t += 500L
        }
        assertEquals(listOf(5), warns)
    }

    @Test
    fun `count stays at zero with no scroll events`() {
        val s = ScrollSession(threshold = 20)
        s.onYouTubeForeground(0L)
        assertEquals(0, s.count)
        s.onYouTubeBackground(5_000L)
        assertEquals(0, s.count)
    }

    @Test
    fun `same milestone never fires twice without crossing back`() {
        // Without snooze, warn at 5 fires once. Even if we kept polling
        // raw events, count moves to 6 — so 5 is "already fired" and the
        // next fire is at 10.
        val s = ScrollSession(threshold = 5)
        s.onYouTubeForeground(0L)
        var t = 1_000L
        val warns = mutableListOf<Int>()
        repeat(10) {
            val d = s.onRawScrollEvent(t)
            if (d is ScrollSession.Decision.WarnAtMilestone) warns.add(d.count)
            t += 500L
        }
        assertEquals(listOf(5, 10), warns)
        assertNotEquals(listOf(5, 5, 10, 10), warns)
    }
}
