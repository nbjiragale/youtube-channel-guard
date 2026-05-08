package com.ycg.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val LOCKDOWN_WINDOWS_JSON = stringPreferencesKey("lockdown_windows_json")

private val WINDOWS_SERIALIZER = ListSerializer(LockdownWindow.serializer())
private val LOCKDOWN_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * Persists the user's lockdown schedule. Reuses the existing channel_guard
 * DataStore (declared in [AllowListRepository]) so we keep a single
 * preferences file.
 */
class LockdownRepository(private val context: Context) {

    val windows: Flow<List<LockdownWindow>> =
        context.guardDataStore.data.map { prefs ->
            decode(prefs[LOCKDOWN_WINDOWS_JSON])
        }

    suspend fun snapshot(): List<LockdownWindow> = windows.first()

    suspend fun upsert(window: LockdownWindow) {
        update { existing ->
            val idx = existing.indexOfFirst { it.id == window.id }
            if (idx >= 0) existing.toMutableList().apply { set(idx, window) }
            else existing + window
        }
    }

    suspend fun remove(id: String) {
        update { existing -> existing.filterNot { it.id == id } }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        update { existing ->
            existing.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
    }

    private suspend fun update(transform: (List<LockdownWindow>) -> List<LockdownWindow>) {
        context.guardDataStore.edit { prefs ->
            val current = decode(prefs[LOCKDOWN_WINDOWS_JSON])
            prefs[LOCKDOWN_WINDOWS_JSON] = encode(transform(current))
        }
    }

    private fun encode(list: List<LockdownWindow>): String =
        LOCKDOWN_JSON.encodeToString(WINDOWS_SERIALIZER, list)

    private fun decode(raw: String?): List<LockdownWindow> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            LOCKDOWN_JSON.decodeFromString(WINDOWS_SERIALIZER, raw)
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
