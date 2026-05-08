package com.ycg.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.guardDataStore by preferencesDataStore(name = "channel_guard")

private val ALLOWED_CHANNELS = stringSetPreferencesKey("allowed_channels")
private val LAST_DETECTED_NAME = stringPreferencesKey("last_detected_name")
private val LAST_DETECTED_AT = longPreferencesKey("last_detected_at")

data class LastDetected(
    val name: String,
    val timestampMs: Long
)

/**
 * Persists the user-configured allow-list of channel names plus the most
 * recently observed channel (used to drive the "approve last seen" UX on the
 * home screen).
 *
 * Channel names are stored exactly as the user typed them but matching is
 * always done through [AllowListMatcher] which trims, strips `@`, etc.
 */
class AllowListRepository(private val context: Context) {

    val allowedChannels: Flow<Set<String>> =
        context.guardDataStore.data.map { it[ALLOWED_CHANNELS] ?: emptySet() }

    val lastDetected: Flow<LastDetected?> =
        context.guardDataStore.data.map { prefs ->
            val name = prefs[LAST_DETECTED_NAME] ?: return@map null
            val ts = prefs[LAST_DETECTED_AT] ?: 0L
            if (name.isBlank()) null else LastDetected(name, ts)
        }

    suspend fun add(name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        context.guardDataStore.edit { prefs ->
            val current = prefs[ALLOWED_CHANNELS]?.toMutableSet() ?: mutableSetOf()
            // Compare via the matcher's normalize() so case / handle / suffix
            // duplicates collapse properly.
            val normalisedNew = AllowListMatcher.normalize(cleaned)
            if (normalisedNew != null && current.none {
                AllowListMatcher.normalize(it) == normalisedNew
            }) {
                current.add(cleaned)
                prefs[ALLOWED_CHANNELS] = current
            }
        }
    }

    suspend fun remove(name: String) {
        val target = AllowListMatcher.normalize(name) ?: return
        context.guardDataStore.edit { prefs ->
            val current = prefs[ALLOWED_CHANNELS]?.toMutableSet() ?: return@edit
            current.removeAll { AllowListMatcher.normalize(it) == target }
            prefs[ALLOWED_CHANNELS] = current
        }
    }

    suspend fun setLastDetected(name: String, timestampMs: Long) {
        context.guardDataStore.edit { prefs ->
            prefs[LAST_DETECTED_NAME] = name
            prefs[LAST_DETECTED_AT] = timestampMs
        }
    }
}
