package com.ycg.app.data

/**
 * Centralised matching logic so the UI, the accessibility service, and tests
 * all agree on what counts as an allowed channel.
 */
object AllowListMatcher {
    fun isAllowed(channelName: String?, allowList: Set<String>): Boolean {
        val candidate = channelName?.trim().orEmpty()
        if (candidate.isEmpty()) return false
        return allowList.any { it.trim().equals(candidate, ignoreCase = true) }
    }
}
