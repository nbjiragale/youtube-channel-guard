package com.ycg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ycg.app.data.AllowListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AllowListRepository(app)

    val channels: StateFlow<Set<String>> = repo.allowedChannels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    fun add(name: String) {
        viewModelScope.launch { repo.add(name) }
    }

    fun remove(name: String) {
        viewModelScope.launch { repo.remove(name) }
    }
}
