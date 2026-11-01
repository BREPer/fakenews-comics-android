package com.fakenews.comics.ui.shelf

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.model.ComicDto
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.net.ImageUrls
import com.fakenews.comics.data.session.ComicEvents
import com.fakenews.comics.data.session.withMeta
import com.fakenews.comics.data.session.withProgress
import com.fakenews.comics.ui.common.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable // 让 Lazy item 的 content lambda 可被 memoize（理由见 data/model/ComicDtos.kt）
data class FavoritesUiState(
    val items: List<ComicDto> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val api: FakenewsApi,
    private val runner: ApiRunner,
    private val imageUrls: ImageUrls,
    events: ComicEvents,
) : ViewModel() {
    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refresh()
        viewModelScope.launch {
            events.events.collect { event ->
                when (event) {
                    is ComicEvents.Event.FavoriteChanged -> if (!event.favorite) {
                        _state.update { s ->
                            s.copy(items = s.items.filterNot { it.id == event.comicId })
                        }
                    }
                    // 库被删/被改：收藏里可能整批条目都没了，没有 comicId 可打补丁 → 重取
                    ComicEvents.Event.LibraryChanged -> refresh()

                    is ComicEvents.Event.ProgressCleared -> _state.update { s ->
                        s.copy(
                            items = s.items.map {
                                if (it.id == event.comicId) {
                                    it.copy(progress = null, readStatus = "unread", lastReadAt = null)
                                } else {
                                    it
                                }
                            },
                        )
                    }
                    is ComicEvents.Event.ProgressChanged -> _state.update { s ->
                        s.copy(
                            items = s.items.map {
                                if (it.id != event.comicId) it else it.withProgress(event)
                            },
                        )
                    }

                    is ComicEvents.Event.MetaChanged -> _state.update { s ->
                        s.copy(
                            items = s.items.map {
                                if (it.id != event.comicId) it else it.withMeta(event)
                            },
                        )
                    }
                }
            }
        }
    }

    fun coverUrl(comicId: String): String? = imageUrls.cover(comicId, 320)

    fun refresh(pullToRefresh: Boolean = false) {
        _state.update {
            it.copy(
                loading = it.items.isEmpty() && !pullToRefresh,
                refreshing = pullToRefresh,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val res = runner.call { api.favorites() }
                _state.update {
                    it.copy(items = res.items, loading = false, refreshing = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: "加载失败"
                _state.update { it.copy(loading = false, refreshing = false, error = message) }
                if (_state.value.items.isNotEmpty()) _messages.send(UiMessage.err(message))
            }
        }
    }
}
