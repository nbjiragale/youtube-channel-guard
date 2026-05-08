package com.ycg.app.data

import kotlinx.serialization.Serializable

/**
 * A channel the user has approved. Stored as a JSON-serialised list inside
 * the existing channel_guard DataStore.
 *
 * Pre-API entries (created before [com.ycg.app.data.api.YouTubeApi]) only
 * have [originalInput]. Once a network connection / API key is available
 * we lazily resolve the rest of the fields and persist them.
 */
@Serializable
data class AllowedChannel(
    /** What the user typed: a name like "MrBeast", a handle "@MrBeast",
     *  a channel URL, or a UC… ID. */
    val originalInput: String,
    /** Canonical channel ID (`UCxxxx`). Empty if not yet resolved. */
    val channelId: String = "",
    /** Display name from YouTube (e.g. "MrBeast"). Empty until resolved. */
    val displayName: String = "",
    /** Public handle including leading `@` (e.g. "@MrBeast"). Empty until resolved. */
    val handle: String = "",
    /** Avatar URL. Empty until resolved. */
    val avatarUrl: String = "",
    /** The channel's uploads playlist ID. Used to fetch the feed. */
    val uploadsPlaylistId: String = "",
    /** When this entry was first added (epoch ms). */
    val addedAt: Long = 0L
) {

    /** True once we have at least the canonical channel ID. */
    val isResolved: Boolean
        get() = channelId.isNotBlank()

    /** Best human-readable label for this channel. */
    val label: String
        get() = displayName.ifBlank { originalInput }
}
