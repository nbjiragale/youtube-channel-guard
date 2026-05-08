package com.ycg.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val ALLOWED_CHANNELS_LEGACY = stringSetPreferencesKey("allowed_channels")
private val ALLOWED_CHANNELS_JSON = stringPreferencesKey("allowed_channels_json")
private val LAST_DETECTED_NAME = stringPreferencesKey("last_detected_name")
private val LAST_DETECTED_AT = longPreferencesKey("last_detected_at")

private val LIST_SERIALIZER = ListSerializer(AllowedChannel.serializer())
private val JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

data class LastDetected(
    val name: String,
    val timestampMs: Long
)

/**
 * Persists the user's allow-list as a list of [AllowedChannel] objects so we
 * can carry channel metadata (id, avatar, uploads playlist) alongside the
 * user-entered string.
 *
 * Legacy plain-string entries from older app versions are migrated lazily
 * the first time the list is read.
 *
 * Matching against accessibility-detected names still goes through
 * [AllowListMatcher], which works on display name / handle strings; the
 * channel ID is a more reliable secondary key once the entry is resolved.
 */
class AllowListRepository(private val context: Context) {

    val channels: Flow<List<AllowedChannel>> =
        context.guardDataStore.data.map { prefs ->
            decode(prefs[ALLOWED_CHANNELS_JSON])
                .ifEmpty { migrateLegacy(prefs[ALLOWED_CHANNELS_LEGACY]) }
        }

    /** Legacy view used by the accessibility service's matcher. */
    val allowedNames: Flow<Set<String>> =
        channels.map { list ->
            buildSet {
                for (ch in list) {
                    if (ch.originalInput.isNotBlank()) add(ch.originalInput)
                    if (ch.displayName.isNotBlank()) add(ch.displayName)
                    if (ch.handle.isNotBlank()) add(ch.handle)
                }
            }
        }

    val lastDetected: Flow<LastDetected?> =
        context.guardDataStore.data.map { prefs ->
            val name = prefs[LAST_DETECTED_NAME] ?: return@map null
            val ts = prefs[LAST_DETECTED_AT] ?: 0L
            if (name.isBlank()) null else LastDetected(name, ts)
        }

    suspend fun snapshot(): List<AllowedChannel> = channels.first()

    /**
     * Add a not-yet-resolved entry from raw user input. Duplicate inputs are
     * collapsed via the matcher. The channel will be resolved by the
     * background resolver later.
     */
    suspend fun addRaw(input: String) {
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return
        upsert {
            val normalisedNew = AllowListMatcher.normalize(cleaned)
            val exists = it.any { existing ->
                AllowListMatcher.normalize(existing.originalInput) == normalisedNew ||
                    AllowListMatcher.normalize(existing.displayName) == normalisedNew ||
                    AllowListMatcher.normalize(existing.handle) == normalisedNew
            }
            if (exists) it
            else it + AllowedChannel(
                originalInput = cleaned,
                addedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * Replace any existing entry that matches [input] (or [resolved.channelId])
     * with the resolved object, preserving its `addedAt` if present.
     */
    suspend fun upsertResolved(input: String, resolved: AllowedChannel) {
        val needle = AllowListMatcher.normalize(input)
        upsert { existing ->
            val byId = existing.indexOfFirst {
                resolved.channelId.isNotBlank() && it.channelId == resolved.channelId
            }
            val byInput = existing.indexOfFirst {
                AllowListMatcher.normalize(it.originalInput) == needle
            }
            val targetIndex = when {
                byId >= 0 -> byId
                byInput >= 0 -> byInput
                else -> -1
            }
            if (targetIndex >= 0) {
                val previous = existing[targetIndex]
                existing.toMutableList().apply {
                    set(
                        targetIndex,
                        resolved.copy(
                            originalInput = previous.originalInput.ifBlank {
                                resolved.originalInput
                            },
                            addedAt = if (previous.addedAt > 0) previous.addedAt
                            else System.currentTimeMillis()
                        )
                    )
                }.toList()
            } else {
                existing + resolved.copy(
                    addedAt = if (resolved.addedAt > 0) resolved.addedAt
                    else System.currentTimeMillis()
                )
            }
        }
    }

    suspend fun remove(input: String) {
        val target = AllowListMatcher.normalize(input) ?: return
        upsert { current ->
            current.filterNot { entry ->
                AllowListMatcher.normalize(entry.originalInput) == target ||
                    AllowListMatcher.normalize(entry.displayName) == target ||
                    AllowListMatcher.normalize(entry.handle) == target
            }
        }
    }

    suspend fun setLastDetected(name: String, timestampMs: Long) {
        context.guardDataStore.edit { prefs ->
            prefs[LAST_DETECTED_NAME] = name
            prefs[LAST_DETECTED_AT] = timestampMs
        }
    }

    private suspend fun upsert(transform: (List<AllowedChannel>) -> List<AllowedChannel>) {
        context.guardDataStore.edit { prefs ->
            val current = decode(prefs[ALLOWED_CHANNELS_JSON])
                .ifEmpty { migrateLegacy(prefs[ALLOWED_CHANNELS_LEGACY]) }
            val updated = transform(current)
            prefs[ALLOWED_CHANNELS_JSON] = encode(updated)
            // Drop the legacy key once we've migrated.
            prefs.remove(ALLOWED_CHANNELS_LEGACY)
        }
    }

    private fun encode(list: List<AllowedChannel>): String =
        JSON.encodeToString(LIST_SERIALIZER, list)

    private fun decode(raw: String?): List<AllowedChannel> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            JSON.decodeFromString(LIST_SERIALIZER, raw)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun migrateLegacy(set: Set<String>?): List<AllowedChannel> {
        if (set.isNullOrEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return set.map { input ->
            AllowedChannel(originalInput = input, addedAt = now)
        }
    }
}
