package com.ycg.app.data

import java.text.Normalizer

/**
 * Pure matching logic. Lives outside the Accessibility Service so it is
 * trivially unit-testable.
 *
 * Both sides of the comparison are normalised through [normalize] so the user
 * can type "MrBeast" in the allow-list and have it still match all of:
 *
 *   - "@MrBeast"               (handle on Shorts / new watch UI)
 *   - "  MrBeast  "            (leading / trailing whitespace)
 *   - "mrbeast"                (case difference)
 *   - "MrBeast - Topic"        (auto-generated music topic channels)
 *   - "MrBeast VEVO"           (label channels)
 *   - "MrBeast Official"       (some artist channels)
 *   - "Mr Beast"               (extra interior whitespace collapses)
 */
object AllowListMatcher {

    fun isAllowed(detectedName: String?, allowList: Set<String>): Boolean {
        // Empty allow-list → guard is inactive. We treat *every* video as
        // allowed rather than blocking everything, because blocking a
        // not-yet-configured guard is hostile.
        if (allowList.isEmpty()) return true

        val candidate = normalize(detectedName) ?: return false
        return allowList.any { entry ->
            normalize(entry)?.let { it == candidate } == true
        }
    }

    /**
     * Reduces a raw user input or YouTube-derived string to the canonical form
     * we compare on. Returns null for blank inputs.
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        // 1. Unicode NFC so "MrBéast" entered with combining accents matches.
        var s = Normalizer.normalize(raw, Normalizer.Form.NFC)

        // 2. Trim, strip leading "@".
        s = s.trim()
        if (s.startsWith("@")) s = s.removePrefix("@").trim()

        // 3. Strip well-known auto-suffixes appended by YouTube to channel
        //    display names. Order matters: " - Topic" before generic " Topic".
        val suffixes = listOf(
            " - Topic",
            " - topic",
            " VEVO",
            " Vevo",
            " vevo",
            " Official",
            " official"
        )
        for (suffix in suffixes) {
            if (s.endsWith(suffix, ignoreCase = true)) {
                s = s.removeRange(s.length - suffix.length, s.length).trim()
                break
            }
        }

        // 4. Strip "• 1.2M subscribers" trailers in case the detected text
        //    came in as a compound string.
        val bullet = s.indexOf('•')
        if (bullet >= 0) s = s.substring(0, bullet).trim()

        // 5. Collapse internal whitespace runs.
        s = s.replace(WHITESPACE_RUN, " ")

        // 6. Lowercase last (after suffix strip so " - Topic" matching is
        //    case-correct above).
        s = s.lowercase()

        return s.ifBlank { null }
    }

    private val WHITESPACE_RUN = Regex("\\s+")
}
