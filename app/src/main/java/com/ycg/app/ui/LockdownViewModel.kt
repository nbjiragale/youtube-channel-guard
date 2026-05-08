package com.ycg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ycg.app.data.LockdownEngine
import com.ycg.app.data.LockdownRepository
import com.ycg.app.data.LockdownWindow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class LockdownViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LockdownRepository(app)

    val windows: StateFlow<List<LockdownWindow>> = repo.windows.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    /**
     * Recomputes every minute. Combined with [windows] so the banner updates
     * the moment the user toggles a window on/off, and at the next minute
     * boundary so the "until 7:00 AM" countdown stays current.
     */
    val activeWindow: StateFlow<LockdownWindow?> =
        combine(windows, ticker(60_000)) { ws, _ ->
            LockdownEngine.activeWindow(LocalDateTime.now(), ws)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    fun upsert(window: LockdownWindow) {
        viewModelScope.launch { repo.upsert(window) }
    }

    fun remove(id: String) {
        viewModelScope.launch { repo.remove(id) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repo.setEnabled(id, enabled) }
    }

    private fun ticker(periodMs: Long) = flow {
        while (true) {
            emit(Unit)
            delay(periodMs)
        }
    }
}
