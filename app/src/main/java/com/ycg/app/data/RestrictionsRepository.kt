package com.ycg.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val BLOCK_SHORTS = booleanPreferencesKey("block_shorts")
private val SCROLL_COUNTER_ENABLED = booleanPreferencesKey("scroll_counter_enabled")
private val SCROLL_COUNTER_THRESHOLD = intPreferencesKey("scroll_counter_threshold")

const val DEFAULT_SCROLL_THRESHOLD = 20
const val MIN_SCROLL_THRESHOLD = 5
const val MAX_SCROLL_THRESHOLD = 100

/**
 * Always-on, content-type-based blocking rules that apply regardless of
 * the allow-list (e.g. "block all Shorts").
 */
class RestrictionsRepository(private val context: Context) {

    val blockShorts: Flow<Boolean> =
        context.guardDataStore.data.map { prefs ->
            prefs[BLOCK_SHORTS] ?: false
        }

    val scrollCounterEnabled: Flow<Boolean> =
        context.guardDataStore.data.map { prefs ->
            prefs[SCROLL_COUNTER_ENABLED] ?: true
        }

    val scrollCounterThreshold: Flow<Int> =
        context.guardDataStore.data.map { prefs ->
            (prefs[SCROLL_COUNTER_THRESHOLD] ?: DEFAULT_SCROLL_THRESHOLD)
                .coerceIn(MIN_SCROLL_THRESHOLD, MAX_SCROLL_THRESHOLD)
        }

    suspend fun snapshot(): Boolean = blockShorts.first()

    suspend fun setBlockShorts(enabled: Boolean) {
        context.guardDataStore.edit { it[BLOCK_SHORTS] = enabled }
    }

    suspend fun setScrollCounterEnabled(enabled: Boolean) {
        context.guardDataStore.edit { it[SCROLL_COUNTER_ENABLED] = enabled }
    }

    suspend fun setScrollCounterThreshold(value: Int) {
        context.guardDataStore.edit {
            it[SCROLL_COUNTER_THRESHOLD] =
                value.coerceIn(MIN_SCROLL_THRESHOLD, MAX_SCROLL_THRESHOLD)
        }
    }
}
