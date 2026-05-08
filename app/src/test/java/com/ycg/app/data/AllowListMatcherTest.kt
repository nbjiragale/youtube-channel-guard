package com.ycg.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowListMatcherTest {

    @Test
    fun `exact match passes`() {
        assertTrue(AllowListMatcher.isAllowed("MrBeast", setOf("MrBeast")))
    }

    @Test
    fun `match is case insensitive`() {
        assertTrue(AllowListMatcher.isAllowed("MRBEAST", setOf("mrbeast")))
        assertTrue(AllowListMatcher.isAllowed("mrbeast", setOf("MrBeast")))
    }

    @Test
    fun `whitespace is trimmed and collapsed on both sides`() {
        assertTrue(AllowListMatcher.isAllowed("  MrBeast  ", setOf(" mrbeast ")))
        assertTrue(AllowListMatcher.isAllowed("Mr  Beast", setOf("Mr Beast")))
    }

    @Test
    fun `leading at-sign is stripped on both sides`() {
        assertTrue(AllowListMatcher.isAllowed("@MrBeast", setOf("MrBeast")))
        assertTrue(AllowListMatcher.isAllowed("MrBeast", setOf("@MrBeast")))
        assertTrue(AllowListMatcher.isAllowed("@MrBeast", setOf("@mrbeast")))
    }

    @Test
    fun `topic and label suffixes are stripped`() {
        assertTrue(AllowListMatcher.isAllowed("MrBeast - Topic", setOf("MrBeast")))
        assertTrue(AllowListMatcher.isAllowed("Drake VEVO", setOf("Drake")))
        assertTrue(AllowListMatcher.isAllowed("Adele Official", setOf("adele")))
    }

    @Test
    fun `bullet trailers like subscriber counts are stripped`() {
        assertTrue(AllowListMatcher.isAllowed("MrBeast • 200M subscribers", setOf("MrBeast")))
    }

    @Test
    fun `substring still does not count as match`() {
        // "MrBeast Gaming" is a different channel from "MrBeast" — must not match.
        assertFalse(AllowListMatcher.isAllowed("MrBeast Gaming", setOf("MrBeast")))
        assertFalse(AllowListMatcher.isAllowed("MrBeast", setOf("MrBeast Gaming")))
    }

    @Test
    fun `empty allow list keeps the guard inactive (everything allowed)`() {
        // This is the deliberate "inactive guard" semantic — see AllowListMatcher.
        assertTrue(AllowListMatcher.isAllowed("MrBeast", emptySet()))
        assertTrue(AllowListMatcher.isAllowed(null, emptySet()))
    }

    @Test
    fun `null or blank channel name with non-empty allow list is blocked`() {
        assertFalse(AllowListMatcher.isAllowed(null, setOf("MrBeast")))
        assertFalse(AllowListMatcher.isAllowed("", setOf("MrBeast")))
        assertFalse(AllowListMatcher.isAllowed("   ", setOf("MrBeast")))
    }

    @Test
    fun `unicode is nfc-normalized`() {
        // The allow-list entry uses combining acute, the detected name uses
        // the precomposed character. They must still match.
        val combining = "Cafe\u0301" // "Café" with combining accent
        val precomposed = "Caf\u00E9" // "Café"
        assertTrue(AllowListMatcher.isAllowed(combining, setOf(precomposed)))
    }

    @Test
    fun `normalize returns null for blanks`() {
        assertNull(AllowListMatcher.normalize(null))
        assertNull(AllowListMatcher.normalize(""))
        assertNull(AllowListMatcher.normalize("   "))
        assertNull(AllowListMatcher.normalize("@"))
    }

    @Test
    fun `normalize strips and lowercases`() {
        assertEquals("mrbeast", AllowListMatcher.normalize(" @MrBeast - Topic "))
    }
}
