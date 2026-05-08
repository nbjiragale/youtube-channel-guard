package com.ycg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ycg.app.data.AllowListRepository
import com.ycg.app.data.AllowedChannel
import com.ycg.app.data.ChannelResolver
import com.ycg.app.data.LastDetected
import com.ycg.app.data.api.YouTubeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AllowListRepository(app)
    private val api = YouTubeApi()
    private val resolver = ChannelResolver(api)

    val channels: StateFlow<List<AllowedChannel>> = repo.channels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val lastDetected: StateFlow<LastDetected?> = repo.lastDetected.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val hasApiKey: Boolean get() = api.hasApiKey()

    sealed interface AddState {
        data object Idle : AddState
        data object Resolving : AddState
        data class Failed(val message: String) : AddState
        data object NotFound : AddState
        data object MissingApiKey : AddState
        data class Added(val channel: AllowedChannel) : AddState
    }

    private val _addState = MutableStateFlow<AddState>(AddState.Idle)
    val addState: StateFlow<AddState> = _addState.asStateFlow()

    fun add(input: String) {
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            // Persist the raw input straight away so the row appears.
            repo.addRaw(cleaned)
            if (!api.hasApiKey()) {
                _addState.value = AddState.MissingApiKey
                return@launch
            }
            _addState.value = AddState.Resolving
            val resolution = withContext(Dispatchers.IO) { resolver.resolve(cleaned) }
            _addState.value = when (resolution) {
                is ChannelResolver.Resolution.Resolved -> {
                    repo.upsertResolved(cleaned, resolution.channel)
                    AddState.Added(resolution.channel)
                }
                ChannelResolver.Resolution.NotFound -> AddState.NotFound
                ChannelResolver.Resolution.ApiKeyMissing -> AddState.MissingApiKey
                is ChannelResolver.Resolution.Error -> AddState.Failed(resolution.message)
            }
        }
    }

    fun ackAddState() {
        _addState.value = AddState.Idle
    }

    fun remove(input: String) {
        viewModelScope.launch { repo.remove(input) }
    }
}
