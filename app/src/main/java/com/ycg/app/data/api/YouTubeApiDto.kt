package com.ycg.app.data.api

import kotlinx.serialization.Serializable

/**
 * Minimal subset of YouTube Data API response shapes that we actually consume.
 * We deliberately tolerate missing fields by giving every property a default.
 */

@Serializable
data class ChannelListResponse(
    val items: List<ChannelItem> = emptyList()
)

@Serializable
data class ChannelItem(
    val id: String = "",
    val snippet: ChannelSnippet = ChannelSnippet(),
    val contentDetails: ChannelContentDetails = ChannelContentDetails()
)

@Serializable
data class ChannelSnippet(
    val title: String = "",
    val customUrl: String = "",
    val description: String = "",
    val thumbnails: Thumbnails = Thumbnails()
)

@Serializable
data class ChannelContentDetails(
    val relatedPlaylists: RelatedPlaylists = RelatedPlaylists()
)

@Serializable
data class RelatedPlaylists(
    val uploads: String = ""
)

@Serializable
data class Thumbnails(
    val default: ThumbnailRef? = null,
    val medium: ThumbnailRef? = null,
    val high: ThumbnailRef? = null,
    val standard: ThumbnailRef? = null,
    val maxres: ThumbnailRef? = null
) {
    /**
     * Best available thumbnail URL, preferring higher resolution but never
     * returning null.
     */
    fun bestUrl(): String =
        (maxres ?: standard ?: high ?: medium ?: default)?.url.orEmpty()
}

@Serializable
data class ThumbnailRef(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class PlaylistItemsResponse(
    val items: List<PlaylistItem> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
data class PlaylistItem(
    val id: String = "",
    val snippet: PlaylistItemSnippet = PlaylistItemSnippet(),
    val contentDetails: PlaylistItemContentDetails = PlaylistItemContentDetails()
)

@Serializable
data class PlaylistItemSnippet(
    val title: String = "",
    val description: String = "",
    val publishedAt: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val thumbnails: Thumbnails = Thumbnails(),
    val resourceId: ResourceId = ResourceId()
)

@Serializable
data class PlaylistItemContentDetails(
    val videoId: String = "",
    val videoPublishedAt: String = ""
)

@Serializable
data class ResourceId(
    val kind: String = "",
    val videoId: String = ""
)

@Serializable
data class VideosListResponse(
    val items: List<VideoItem> = emptyList()
)

@Serializable
data class VideoItem(
    val id: String = "",
    val snippet: VideoSnippet = VideoSnippet(),
    val contentDetails: VideoContentDetails = VideoContentDetails()
)

@Serializable
data class VideoSnippet(
    val title: String = "",
    val publishedAt: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val thumbnails: Thumbnails = Thumbnails()
)

@Serializable
data class VideoContentDetails(
    val duration: String = "" // ISO 8601: "PT1H2M3S"
)
