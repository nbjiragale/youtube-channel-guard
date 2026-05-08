package com.ycg.app.data

import com.ycg.app.data.api.ApiKeyMissingException
import com.ycg.app.data.api.YouTubeApi
import kotlinx.serialization.Serializable

/**
 * One row in the curated feed.
 */
@Serializable
data class FeedVideo(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val channelAvatarUrl: String,
    val thumbnailUrl: String,
    /** ISO 8601 timestamp (yyyy-MM-ddThh:mm:ssZ). */
    val publishedAt: String,
    /** ISO 8601 duration ("PT4M13S"). May be empty if not yet fetched. */
    val durationIso: String = ""
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
}

/**
 * Aggregates the latest uploads across every allowed channel into a single
 * chronological feed.
 *
 * Quota cost (per refresh):
 *  - One `playlistItems.list` call per allowed channel that has a known
 *    uploads playlist ID (1 unit each).
 *  - One `videos.list` call per 50 video-IDs to fill in durations (1 unit each).
 *
 * For 10 allowed channels that's ~11 units per refresh — well inside the
 * 10,000-units/day free quota.
 */
class FeedRepository(
    private val api: YouTubeApi,
    private val allowList: AllowListRepository,
    private val resolver: ChannelResolver
) {

    sealed interface FeedResult {
        data class Ok(val videos: List<FeedVideo>) : FeedResult
        data object ApiKeyMissing : FeedResult
        data object Empty : FeedResult
        data class Error(val message: String) : FeedResult
    }

    suspend fun refresh(perChannelLimit: Int = 10): FeedResult {
        if (!api.hasApiKey()) return FeedResult.ApiKeyMissing

        val channels = allowList.snapshot()
        if (channels.isEmpty()) return FeedResult.Empty

        // Resolve any channel whose uploads playlist we don't yet know.
        for (channel in channels.filter { it.uploadsPlaylistId.isBlank() }) {
            try {
                val resolution = resolver.resolve(channel.originalInput)
                if (resolution is ChannelResolver.Resolution.Resolved) {
                    allowList.upsertResolved(channel.originalInput, resolution.channel)
                }
            } catch (_: ApiKeyMissingException) {
                return FeedResult.ApiKeyMissing
            } catch (_: Throwable) {
                // Carry on — we'll just skip this channel for this refresh.
            }
        }

        // Re-snapshot now that resolution may have populated playlists.
        val resolved = allowList.snapshot().filter { it.uploadsPlaylistId.isNotBlank() }
        if (resolved.isEmpty()) {
            return FeedResult.Error(
                "Couldn't look up any channels yet — check your API key and try again."
            )
        }

        val videos = mutableListOf<FeedVideo>()
        var anySuccess = false
        var lastError: String? = null

        for (channel in resolved) {
            val result = api.playlistItems(channel.uploadsPlaylistId, perChannelLimit)
            result.fold(
                onSuccess = { resp ->
                    anySuccess = true
                    for (item in resp.items) {
                        val videoId = item.contentDetails.videoId
                            .ifBlank { item.snippet.resourceId.videoId }
                        if (videoId.isBlank()) continue
                        videos.add(
                            FeedVideo(
                                videoId = videoId,
                                title = item.snippet.title,
                                channelId = channel.channelId,
                                channelTitle = channel.displayName.ifBlank {
                                    item.snippet.channelTitle
                                },
                                channelAvatarUrl = channel.avatarUrl,
                                thumbnailUrl = item.snippet.thumbnails.bestUrl(),
                                publishedAt = item.contentDetails.videoPublishedAt
                                    .ifBlank { item.snippet.publishedAt }
                            )
                        )
                    }
                },
                onFailure = { err ->
                    lastError = err.message
                }
            )
        }

        if (!anySuccess) {
            return FeedResult.Error(lastError ?: "Couldn't refresh feed.")
        }

        // Fill in durations in chunks of 50.
        val byId = videos.associateBy { it.videoId }.toMutableMap()
        videos.map { it.videoId }.chunked(50).forEach { chunk ->
            api.videosByIds(chunk).onSuccess { resp ->
                for (item in resp.items) {
                    val existing = byId[item.id] ?: continue
                    byId[item.id] = existing.copy(durationIso = item.contentDetails.duration)
                }
            }
        }

        val merged = byId.values
            .sortedByDescending { it.publishedAt }
            .toList()
        return FeedResult.Ok(merged)
    }
}
