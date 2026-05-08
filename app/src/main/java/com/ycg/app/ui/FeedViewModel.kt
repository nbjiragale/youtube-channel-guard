package com.ycg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ycg.app.data.AllowListRepository
import com.ycg.app.data.ChannelResolver
import com.ycg.app.data.FeedRepository
import com.ycg.app.data.FeedVideo
import com.ycg.app.data.api.YouTubeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val api = YouTubeApi()
    private val allowList = AllowListRepository(app)
    private val resolver = ChannelResolver(api)
    private val repo = FeedRepository(api, allowList, resolver)

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Empty(val reason: Reason) : State
        data class Loaded(val videos: List<FeedVideo>) : State
        data class Error(val message: String) : State

        enum class Reason { NoApiKey, NoChannels, AllUnresolved }
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = State.Loading
            val result = withContext(Dispatchers.IO) { repo.refresh() }
            _state.value = when (result) {
                is FeedRepository.FeedResult.Ok -> State.Loaded(result.videos)
                FeedRepository.FeedResult.ApiKeyMissing -> State.Empty(State.Reason.NoApiKey)
                FeedRepository.FeedResult.Empty -> State.Empty(State.Reason.NoChannels)
                is FeedRepository.FeedResult.Error -> State.Error(result.message)
            }
        }
    }
}
