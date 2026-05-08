package com.ycg.app.data.api

import android.util.Log
import com.ycg.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around YouTube Data API v3.
 *
 * - The API key comes from `BuildConfig.YOUTUBE_API_KEY`, populated from
 *   `local.properties` (see `app/build.gradle.kts`). If the key is empty, all
 *   calls return [Result.failure] with [ApiKeyMissingException].
 * - All blocking calls; intended to be invoked from a coroutine on `Dispatchers.IO`.
 * - Results are wrapped in `kotlin.Result` so the UI can distinguish auth /
 *   quota / network errors from successful responses.
 */
class YouTubeApi(
    private val apiKey: String = BuildConfig.YOUTUBE_API_KEY
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor { msg -> Log.d(TAG, msg) }
                .setLevel(HttpLoggingInterceptor.Level.BASIC)
        )
        .build()

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    /** Lookup a channel by its `@handle` (with or without leading `@`). */
    fun channelByHandle(handle: String): Result<ChannelItem?> {
        if (!hasApiKey()) return Result.failure(ApiKeyMissingException())
        val cleaned = handle.trim().let { if (it.startsWith("@")) it else "@$it" }
        val url = baseChannels()
            .addQueryParameter("forHandle", cleaned)
            .build()
        return runCatching { fetchChannelsList(url.toString()).items.firstOrNull() }
    }

    /** Lookup a channel by its legacy username. Old channels only. */
    fun channelByUsername(username: String): Result<ChannelItem?> {
        if (!hasApiKey()) return Result.failure(ApiKeyMissingException())
        val url = baseChannels()
            .addQueryParameter("forUsername", username.trim())
            .build()
        return runCatching { fetchChannelsList(url.toString()).items.firstOrNull() }
    }

    /** Lookup a channel by its canonical UC… channel ID. */
    fun channelById(channelId: String): Result<ChannelItem?> {
        if (!hasApiKey()) return Result.failure(ApiKeyMissingException())
        val url = baseChannels()
            .addQueryParameter("id", channelId.trim())
            .build()
        return runCatching { fetchChannelsList(url.toString()).items.firstOrNull() }
    }

    /**
     * Fetch the most recent uploads for a given uploads-playlist ID
     * (every channel has one — see `ChannelContentDetails.relatedPlaylists.uploads`).
     */
    fun playlistItems(
        playlistId: String,
        maxResults: Int = 25
    ): Result<PlaylistItemsResponse> {
        if (!hasApiKey()) return Result.failure(ApiKeyMissingException())
        val url = "https://www.googleapis.com/youtube/v3/playlistItems".toHttpUrl()
            .newBuilder()
            .addQueryParameter("part", "snippet,contentDetails")
            .addQueryParameter("playlistId", playlistId)
            .addQueryParameter("maxResults", maxResults.coerceIn(1, 50).toString())
            .addQueryParameter("key", apiKey)
            .build()
        return runCatching {
            val body = get(url.toString())
            json.decodeFromString(PlaylistItemsResponse.serializer(), body)
        }
    }

    /**
     * Fetch detailed metadata (incl. duration) for up to 50 videos at once.
     */
    fun videosByIds(ids: List<String>): Result<VideosListResponse> {
        if (!hasApiKey()) return Result.failure(ApiKeyMissingException())
        if (ids.isEmpty()) return Result.success(VideosListResponse())
        val url = "https://www.googleapis.com/youtube/v3/videos".toHttpUrl()
            .newBuilder()
            .addQueryParameter("part", "snippet,contentDetails")
            .addQueryParameter("id", ids.take(50).joinToString(","))
            .addQueryParameter("key", apiKey)
            .build()
        return runCatching {
            val body = get(url.toString())
            json.decodeFromString(VideosListResponse.serializer(), body)
        }
    }

    private fun fetchChannelsList(url: String): ChannelListResponse {
        val body = get(url)
        return json.decodeFromString(ChannelListResponse.serializer(), body)
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, body.take(500))
            }
            return body
        }
    }

    private fun baseChannels() =
        "https://www.googleapis.com/youtube/v3/channels".toHttpUrl()
            .newBuilder()
            .addQueryParameter("part", "snippet,contentDetails")
            .addQueryParameter("key", apiKey)

    companion object {
        private const val TAG = "YTGuardApi"
    }
}

class ApiKeyMissingException :
    Exception("YouTube API key is not set. Add youtube.api.key=<KEY> to local.properties.")

class ApiException(val code: Int, val bodyExcerpt: String) :
    Exception("YouTube API call failed: HTTP $code — $bodyExcerpt")
