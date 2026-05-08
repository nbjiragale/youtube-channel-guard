package com.ycg.app.data

import com.ycg.app.data.api.ApiKeyMissingException
import com.ycg.app.data.api.ChannelItem
import com.ycg.app.data.api.YouTubeApi

/**
 * Translates a free-form user input string into a fully-resolved
 * [AllowedChannel] by calling the YouTube Data API.
 *
 * Accepted shapes:
 * - `@MrBeast`            → channels.list?forHandle=@MrBeast
 * - `MrBeast`             → tries forHandle=@MrBeast, falls back to forUsername
 * - `https://youtube.com/@MrBeast` and variants → extracts handle, then forHandle
 * - `https://youtube.com/channel/UC…` → extracts ID, channels.list?id=
 * - `UC…` (24-char ID)    → channels.list?id=
 */
class ChannelResolver(private val api: YouTubeApi) {

    sealed interface Resolution {
        data class Resolved(val channel: AllowedChannel) : Resolution
        data object NotFound : Resolution
        data object ApiKeyMissing : Resolution
        data class Error(val message: String) : Resolution
    }

    fun resolve(input: String): Resolution {
        if (!api.hasApiKey()) return Resolution.ApiKeyMissing
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return Resolution.NotFound

        val byUrl = parseUrl(cleaned)
        val attempts: List<() -> ChannelItem?> = buildList {
            when (byUrl) {
                is UrlInput.Handle -> add { api.channelByHandle(byUrl.handle).getOrThrow() }
                is UrlInput.ChannelId -> add { api.channelById(byUrl.id).getOrThrow() }
                null -> {
                    if (cleaned.startsWith("UC") && cleaned.length in 20..30) {
                        add { api.channelById(cleaned).getOrThrow() }
                    }
                    val handleGuess = if (cleaned.startsWith("@")) cleaned else "@$cleaned"
                    add { api.channelByHandle(handleGuess).getOrThrow() }
                    add { api.channelByUsername(cleaned).getOrThrow() }
                }
            }
        }

        for (attempt in attempts) {
            try {
                val item = attempt()
                if (item != null && item.id.isNotBlank()) {
                    return Resolution.Resolved(item.toAllowedChannel(originalInput = cleaned))
                }
            } catch (e: ApiKeyMissingException) {
                return Resolution.ApiKeyMissing
            } catch (t: Throwable) {
                // Swallow and try the next attempt; we'll surface the last error
                // if all attempts fail.
                lastError = t
            }
        }
        val err = lastError
        return if (err == null) Resolution.NotFound
        else Resolution.Error(err.message ?: err.javaClass.simpleName)
    }

    @Volatile
    private var lastError: Throwable? = null

    private sealed interface UrlInput {
        data class Handle(val handle: String) : UrlInput
        data class ChannelId(val id: String) : UrlInput
    }

    private fun parseUrl(text: String): UrlInput? {
        val lower = text.lowercase()
        if (!lower.startsWith("http")) return null
        // youtube.com/@handle  | youtube.com/channel/UC… | youtu.be/<videoid> (skip)
        Regex("""(?:youtube\.com)/@([A-Za-z0-9._-]+)""").find(text)?.let {
            return UrlInput.Handle("@" + it.groupValues[1])
        }
        Regex("""(?:youtube\.com)/channel/(UC[A-Za-z0-9_-]+)""").find(text)?.let {
            return UrlInput.ChannelId(it.groupValues[1])
        }
        return null
    }
}

private fun ChannelItem.toAllowedChannel(originalInput: String): AllowedChannel =
    AllowedChannel(
        originalInput = originalInput,
        channelId = id,
        displayName = snippet.title,
        handle = snippet.customUrl
            .takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("@")) it else "@$it" }
            .orEmpty(),
        avatarUrl = snippet.thumbnails.bestUrl(),
        uploadsPlaylistId = contentDetails.relatedPlaylists.uploads
    )
