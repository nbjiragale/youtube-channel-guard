package com.ycg.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month

class LockdownEngineTest {

    private fun w(
        startMin: Int,
        endMin: Int,
        days: Set<Int> = LockdownWindow.ALL_DAYS,
        enabled: Boolean = true,
        label: String = ""
    ) = LockdownWindow(
        startMinuteOfDay = startMin,
        endMinuteOfDay = endMin,
        daysOfWeek = days,
        enabled = enabled,
        label = label
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute)

    // 2025-01-06 is a Monday (ISO 1).
    private val mondayMorning = at(2025, 1, 6, 6, 0)
    private val mondayEvening = at(2025, 1, 6, 23, 30)
    private val tuesdayEarlyMorning = at(2025, 1, 7, 2, 30)
    private val saturday = at(2025, 1, 11, 12, 0)
    private val sunday = at(2025, 1, 12, 12, 0)

    @Test
    fun `empty list returns null`() {
        assertNull(LockdownEngine.activeWindow(mondayMorning, emptyList()))
    }

    @Test
    fun `disabled window does not match`() {
        val window = w(0, 24 * 60 - 1, enabled = false)
        assertNull(LockdownEngine.activeWindow(mondayMorning, listOf(window)))
    }

    @Test
    fun `same-day window — inside`() {
        val office = w(9 * 60, 17 * 60)
        val now = at(2025, 1, 6, 12, 0)
        assertSame(office, LockdownEngine.activeWindow(now, listOf(office)))
    }

    @Test
    fun `same-day window — exactly at start is inside`() {
        val office = w(9 * 60, 17 * 60)
        val now = at(2025, 1, 6, 9, 0)
        assertSame(office, LockdownEngine.activeWindow(now, listOf(office)))
    }

    @Test
    fun `same-day window — exactly at end is outside`() {
        val office = w(9 * 60, 17 * 60)
        val now = at(2025, 1, 6, 17, 0)
        assertNull(LockdownEngine.activeWindow(now, listOf(office)))
    }

    @Test
    fun `same-day window — outside`() {
        val office = w(9 * 60, 17 * 60)
        val now = at(2025, 1, 6, 8, 59)
        assertNull(LockdownEngine.activeWindow(now, listOf(office)))
    }

    @Test
    fun `overnight window — late evening today is inside`() {
        val sleep = w(23 * 60, 7 * 60)
        assertSame(sleep, LockdownEngine.activeWindow(mondayEvening, listOf(sleep)))
    }

    @Test
    fun `overnight window — early morning following day is inside`() {
        val sleep = w(23 * 60, 7 * 60)
        assertSame(sleep, LockdownEngine.activeWindow(tuesdayEarlyMorning, listOf(sleep)))
    }

    @Test
    fun `overnight window — daytime is outside`() {
        val sleep = w(23 * 60, 7 * 60)
        val now = at(2025, 1, 6, 12, 0)
        assertNull(LockdownEngine.activeWindow(now, listOf(sleep)))
    }

    @Test
    fun `overnight window restricted to weekdays — Sunday early morning still matches because it started Saturday night`() {
        val sleep = w(
            startMin = 23 * 60,
            endMin = 7 * 60,
            days = setOf(6) // Saturday
        )
        val sundayPredawn = at(2025, 1, 12, 3, 0) // Sunday morning
        assertSame(sleep, LockdownEngine.activeWindow(sundayPredawn, listOf(sleep)))
    }

    @Test
    fun `overnight window restricted to weekdays — Sunday late evening does not match because Sunday isn't in days`() {
        val sleep = w(
            startMin = 23 * 60,
            endMin = 7 * 60,
            days = setOf(1, 2, 3, 4, 5)
        )
        val sundayLate = at(2025, 1, 12, 23, 30)
        assertNull(LockdownEngine.activeWindow(sundayLate, listOf(sleep)))
    }

    @Test
    fun `weekday-only window does not fire on weekends`() {
        val office = w(
            startMin = 9 * 60,
            endMin = 17 * 60,
            days = setOf(1, 2, 3, 4, 5)
        )
        assertNull(LockdownEngine.activeWindow(saturday, listOf(office)))
        assertNull(LockdownEngine.activeWindow(sunday, listOf(office)))
    }

    @Test
    fun `weekday-only window fires on a weekday`() {
        val office = w(
            startMin = 9 * 60,
            endMin = 17 * 60,
            days = setOf(1, 2, 3, 4, 5)
        )
        val mondayNoon = at(2025, 1, 6, 12, 0)
        assertSame(office, LockdownEngine.activeWindow(mondayNoon, listOf(office)))
    }

    @Test
    fun `multiple windows — first match wins`() {
        val a = w(0, 1, label = "trivial")
        val b = w(9 * 60, 17 * 60, label = "office")
        val now = at(2025, 1, 6, 10, 0)
        assertEquals(b, LockdownEngine.activeWindow(now, listOf(a, b)))
    }

    @Test
    fun `endAt — same-day window`() {
        val office = w(9 * 60, 17 * 60)
        val now = at(2025, 1, 6, 12, 0)
        val end = office.endAt(now)
        assertEquals(LocalDateTime.of(2025, Month.JANUARY, 6, 17, 0), end)
    }

    @Test
    fun `endAt — overnight window from evening side rolls to next day`() {
        val sleep = w(23 * 60, 7 * 60)
        val end = sleep.endAt(mondayEvening)
        assertEquals(LocalDateTime.of(2025, Month.JANUARY, 7, 7, 0), end)
    }

    @Test
    fun `endAt — overnight window from morning side stays today`() {
        val sleep = w(23 * 60, 7 * 60)
        val end = sleep.endAt(tuesdayEarlyMorning)
        assertEquals(LocalDateTime.of(2025, Month.JANUARY, 7, 7, 0), end)
    }

    @Test
    fun `crossesMidnight detects overnight ranges`() {
        assertEquals(false, w(9 * 60, 17 * 60).crossesMidnight)
        assertEquals(true, w(23 * 60, 7 * 60).crossesMidnight)
        assertEquals(true, w(60, 60).crossesMidnight) // start == end → also "all day off"
    }

    @Test
    fun `start and end times`() {
        val window = w(start23h45 = 23 * 60 + 45, end7h15 = 7 * 60 + 15)
        assertEquals(LocalTime.of(23, 45), window.startTime)
        assertEquals(LocalTime.of(7, 15), window.endTime)
    }

    private fun w(start23h45: Int, end7h15: Int) = LockdownWindow(
        startMinuteOfDay = start23h45,
        endMinuteOfDay = end7h15
    )
}
