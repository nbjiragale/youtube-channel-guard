package com.ycg.app.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Human-readable formatting helpers used by the feed UI. */
object Format {

    /** "PT1H2M3S" → "1:02:03"; "PT4M13S" → "4:13"; "PT45S" → "0:45". */
    fun duration(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val d = Duration.parse(iso)
            val total = d.seconds
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            if (h > 0) "%d:%02d:%02d".format(h, m, s)
            else "%d:%02d".format(m, s)
        } catch (_: DateTimeParseException) {
            ""
        }
    }

    /** "2024-12-30T11:22:00Z" → "2 days ago". */
    fun relativeTime(iso: String, now: Instant = Instant.now()): String {
        if (iso.isBlank()) return ""
        return try {
            val t = Instant.parse(iso)
            val secs = ChronoUnit.SECONDS.between(t, now).coerceAtLeast(0)
            when {
                secs < 60 -> "just now"
                secs < 3600 -> "${secs / 60}m ago"
                secs < 86_400 -> "${secs / 3600}h ago"
                secs < 604_800 -> "${secs / 86_400}d ago"
                secs < 2_592_000 -> "${secs / 604_800}w ago"
                secs < 31_536_000 -> "${secs / 2_592_000}mo ago"
                else -> "${secs / 31_536_000}y ago"
            }
        } catch (_: DateTimeParseException) {
            ""
        }
    }

    fun absoluteDate(iso: String): String {
        return try {
            val t = Instant.parse(iso)
            val ldt = LocalDateTime.ofInstant(t, ZoneId.systemDefault())
            ldt.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        } catch (_: DateTimeParseException) {
            ""
        }
    }
}
