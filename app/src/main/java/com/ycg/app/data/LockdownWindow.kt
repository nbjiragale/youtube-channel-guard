package com.ycg.app.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * A user-configured period during which *all* YouTube videos are blocked,
 * regardless of whether the channel is on the allow-list.
 *
 * Times are stored as a "minute of day" integer (0..1439) so persisting them
 * is timezone-stable. Days of the week use ISO numbering (1=Mon..7=Sun).
 *
 * If [endMinuteOfDay] <= [startMinuteOfDay] the window spans midnight (e.g.
 * 23:00 → 07:00). [crossesMidnight] reports this for callers.
 */
@Serializable
data class LockdownWindow(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    /**
     * Days *on which the window starts*. For overnight windows that cross
     * midnight, the morning portion of the window is matched against the
     * **previous** day of the week — i.e. a Mon→Tue overnight window is
     * configured as `daysOfWeek={MONDAY}`.
     */
    val daysOfWeek: Set<Int> = ALL_DAYS,
    val enabled: Boolean = true
) {

    val crossesMidnight: Boolean
        get() = endMinuteOfDay <= startMinuteOfDay

    val startTime: LocalTime
        get() = LocalTime.of(startMinuteOfDay / 60, startMinuteOfDay % 60)

    val endTime: LocalTime
        get() = LocalTime.of(endMinuteOfDay / 60, endMinuteOfDay % 60)

    /**
     * The wall-clock instant at which this window's *current* occurrence
     * ends, given a [now] inside the window. For non-overnight windows that
     * is today at [endTime]; for overnight windows that started yesterday
     * it is today at [endTime]; for overnight windows that started today it
     * is tomorrow at [endTime].
     */
    fun endAt(now: LocalDateTime): LocalDateTime {
        val nowMin = now.hour * 60 + now.minute
        val end = endTime
        return if (crossesMidnight && nowMin >= startMinuteOfDay) {
            now.toLocalDate().plusDays(1).atTime(end)
        } else {
            now.toLocalDate().atTime(end)
        }
    }

    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
        val WEEKDAYS: Set<Int> = (1..5).toSet()
        val WEEKEND: Set<Int> = setOf(6, 7)

        fun dayName(iso: Int): String = when (iso) {
            1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"
            5 -> "Fri"; 6 -> "Sat"; 7 -> "Sun"
            else -> ""
        }
    }
}

/**
 * Pure, deterministic decision logic for whether a moment in time is inside
 * any of a list of [LockdownWindow]s.
 */
object LockdownEngine {

    /**
     * Returns the first matching window that contains [now], or null if
     * none of [windows] are active.
     *
     * Edge semantics: a window of `[09:00, 17:00]` is active for `09:00 <= t < 17:00`.
     */
    fun activeWindow(now: LocalDateTime, windows: List<LockdownWindow>): LockdownWindow? {
        if (windows.isEmpty()) return null
        val nowMin = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = previousDay(today)

        for (w in windows) {
            if (!w.enabled) continue
            if (w.daysOfWeek.isEmpty()) continue

            if (w.crossesMidnight) {
                // Evening portion (today, after start)
                if (today in w.daysOfWeek && nowMin >= w.startMinuteOfDay) return w
                // Morning portion (today, before end — but the window started yesterday)
                if (yesterday in w.daysOfWeek && nowMin < w.endMinuteOfDay) return w
            } else {
                if (today in w.daysOfWeek &&
                    nowMin >= w.startMinuteOfDay &&
                    nowMin < w.endMinuteOfDay
                ) return w
            }
        }
        return null
    }

    private fun previousDay(iso: Int): Int = if (iso == 1) 7 else iso - 1
}

internal fun DayOfWeek.iso(): Int = this.value
