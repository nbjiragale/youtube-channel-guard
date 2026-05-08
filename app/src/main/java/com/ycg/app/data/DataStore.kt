package com.ycg.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single shared Preferences DataStore used by every repository in the app.
 */
internal val Context.guardDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "channel_guard")
