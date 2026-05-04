package com.ycg.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.allowListDataStore by preferencesDataStore(name = "channel_guard")

private val ALLOWED_CHANNELS = stringSetPreferencesKey("allowed_channels")

/**
 * Persists the user-configured allow-list of channel names.
 *
 * Channel names are stored exactly as the user typed them, but matching is
 * always done case-insensitively against the trimmed string from YouTube's UI.
 */
class AllowListRepository(private val context: Context) {

    val allowedChannels: Flow<Set<String>> =
        context.allowListDataStore.data.map { prefs ->
            prefs[ALLOWED_CHANNELS] ?: emptySet()
        }

    suspend fun add(name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        context.allowListDataStore.edit { prefs ->
            val current = prefs[ALLOWED_CHANNELS]?.toMutableSet() ?: mutableSetOf()
            // De-duplicate case-insensitively — keep the user's casing for display.
            if (current.none { it.equals(cleaned, ignoreCase = true) }) {
                current.add(cleaned)
                prefs[ALLOWED_CHANNELS] = current
            }
        }
    }

    suspend fun remove(name: String) {
        context.allowListDataStore.edit { prefs ->
            val current = prefs[ALLOWED_CHANNELS]?.toMutableSet() ?: return@edit
            current.removeAll { it.equals(name, ignoreCase = true) }
            prefs[ALLOWED_CHANNELS] = current
        }
    }
}
