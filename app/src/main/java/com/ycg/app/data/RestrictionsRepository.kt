package com.ycg.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val BLOCK_SHORTS = booleanPreferencesKey("block_shorts")

/**
 * Always-on, content-type-based blocking rules that apply regardless of
 * the allow-list (e.g. "block all Shorts").
 */
class RestrictionsRepository(private val context: Context) {

    val blockShorts: Flow<Boolean> =
        context.guardDataStore.data.map { prefs ->
            prefs[BLOCK_SHORTS] ?: false
        }

    suspend fun snapshot(): Boolean = blockShorts.first()

    suspend fun setBlockShorts(enabled: Boolean) {
        context.guardDataStore.edit { it[BLOCK_SHORTS] = enabled }
    }
}
