package com.ycg.app.data

import org.junit.Assert.assertFalse
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
    fun `whitespace is trimmed on both sides`() {
        assertTrue(AllowListMatcher.isAllowed("  MrBeast  ", setOf(" mrbeast ")))
    }

    @Test
    fun `substring does not count as match`() {
        assertFalse(AllowListMatcher.isAllowed("MrBeast Gaming", setOf("MrBeast")))
        assertFalse(AllowListMatcher.isAllowed("MrBeast", setOf("MrBeast Gaming")))
    }

    @Test
    fun `empty channel name is never allowed`() {
        assertFalse(AllowListMatcher.isAllowed(null, setOf("MrBeast")))
        assertFalse(AllowListMatcher.isAllowed("", setOf("MrBeast")))
        assertFalse(AllowListMatcher.isAllowed("   ", setOf("MrBeast")))
    }

    @Test
    fun `empty allow list blocks everything`() {
        assertFalse(AllowListMatcher.isAllowed("MrBeast", emptySet()))
    }
}
