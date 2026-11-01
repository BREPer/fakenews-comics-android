package com.fakenews.comics.ui.shelf

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.model.HistoryItemDto
import com.fakenews.comics.data.model.PutProgressBody
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.net.ImageUrls
import com.fakenews.comics.data.session.ComicEvents
import com.fakenews.comics.data.session.withMeta
import com.fakenews.comics.ui.common.ToastKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 每页条数。书架页要用它判断「到底了」那句话该不该出现（Web 的 `length >= 30`）。 */
internal const val PAGE_SIZE = 30

/** 服务端 `GET /api/history` 的 limit 上限（`httputil.QueryInt(r, "limit", 50, 1, 200)`）。 */
private const val HISTORY_MAX_LIMIT = 200

@Immutable // 让 Lazy item 的 content lambda 可被 memoize（理由见 data/model/ComicDtos.kt）
data class HistoryUiState(
    val items: List<HistoryItemDto> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreFailed: Boolean = false,
    val hasMore: Boolean = false,
)

sealed interface HistoryMessage {
    /** 删除成功，界面弹 6 秒带撤销的提示；窗口结束后按 comicId 落定删除。 */
    data class Undoable(val comicId: String, val title: String) : HistoryMessage
    /** 普通回执。kind 决定图标与停留时长（失败 4.6s、成功 2.4s）。 */
    data class Toast(val text: String, val kind: ToastKind = ToastKind.INFO) : HistoryMessage
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val api: FakenewsApi,
    private val runner: ApiRunner,
    private val imageUrls: ImageUrls,
    private val events: ComicEvents,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _messages = Channel<HistoryMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private data class PendingDelete(
        val item: HistoryItemDto,
        val index: Int,
        val updatedAt: Long,
    )

    private var pending: PendingDelete? = null
    private var deleteJob: Job? = null
    private var loadToken = 0
    private var stale = false

    init {
        fetch(reset = true, pullToRefresh = false)
        viewModelScope.launch {
            events.events.collect { event ->
                when (event) {
                    is ComicEvents.Event.ProgressCleared -> _state.update { s ->
                        if (s.items.none { it.id == event.comicId }) {
                            s
                        } else {
                            s.copy(
                                items = s.items.filterNot { it.id == event.comicId },
                                total = (s.total - 1).coerceAtLeast(0),
                            )
                        }
                    }
                    is ComicEvents.Event.FavoriteChanged -> Unit

                    // 库被删/被改：这一页的快照整个失效，重取（同样没有 comicId 可打补丁）
                    ComicEvents.Event.LibraryChanged -> fetch(reset = true, pullToRefresh = false)

                    // 列表按最后阅读时间排序，就地改一行会让刚读完的漫画留在原位
                    is ComicEvents.Event.ProgressChanged -> stale = true

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

    fun coverUrl(comicId: String): String? = imageUrls.cover(comicId, 160)

    fun refresh(pullToRefresh: Boolean = false) {
        fetch(reset = true, pullToRefresh = pullToRefresh, limit = PAGE_SIZE)
    }

    /**
     * 从阅读器回来时才重取：读一本会把它顶到列表最前，就地补数据补不出这个顺序。
     *
     * 重取时**保持已加载的深度**。只重取第一页的话，用户翻了 5 页记录（150 条）、
     * 从阅读器返回后会只剩 30 条 —— 滚动位置还在，内容却没了，等于每次读完一本书
     * 都要重新往下翻。服务端 `GET /api/history` 的 limit 上限是 200（`QueryInt` 的
     * 第四、五个参数是 1/200），所以最多恢复 6 页多一点，再深就靠续拉。
     */
    fun refreshIfStale() {
        if (!stale) return
        stale = false
        val depth = _state.value.items.size.coerceIn(PAGE_SIZE, HISTORY_MAX_LIMIT)
        fetch(reset = true, pullToRefresh = false, limit = depth)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || s.loadingMore || !s.hasMore || s.loadMoreFailed) return
        fetch(reset = false, pullToRefresh = false)
    }

    fun retryLoadMore() {
        _state.update { it.copy(loadMoreFailed = false) }
        loadMore()
    }

    /**
     * 先本地移除再删服务端；撤销窗口内点「撤销」会把原时间戳写回，
     * 记录仍留在原来的位置（服务端 PUT /api/progress 支持 updatedAt）。
     */
    fun delete(item: HistoryItemDto) {
        if (_state.value.items.none { it.id == item.id }) return
        commitPendingDelete()

        val index = _state.value.items.indexOfFirst { it.id == item.id }
        val p = PendingDelete(
            item = item,
            index = index,
            updatedAt = item.lastReadAt ?: item.progress?.updatedAt ?: 0L,
        )
        pending = p
        _state.update { s ->
            s.copy(
                items = s.items.filterNot { it.id == item.id },
                total = (s.total - 1).coerceAtLeast(0),
            )
        }
        viewModelScope.launch { _messages.send(HistoryMessage.Undoable(item.id, item.title)) }

        deleteJob = viewModelScope.launch {
            try {
                runner.call { api.removeHistory(item.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (pending === p) pending = null
                restoreLocally(p)
                _messages.send(HistoryMessage.Toast("删除失败：${e.message ?: "未知错误"}", ToastKind.ERROR))
            }
        }
    }

    /** 撤销窗口过期（或离开页面）：确认删除，让书架卡片清掉进度条。 */
    fun commitPendingDelete() {
        val p = pending ?: return
        pending = null
        events.emit(ComicEvents.Event.ProgressCleared(p.item.id))
    }

    /** 只结束对应条目的窗口：连续删除时，旧提示超时不能误结算新条目。 */
    fun commitDelete(comicId: String) {
        val p = pending ?: return
        if (p.item.id != comicId) return
        pending = null
        events.emit(ComicEvents.Event.ProgressCleared(p.item.id))
    }

    fun undo(comicId: String) {
        val p = pending ?: return
        if (p.item.id != comicId) return
        pending = null
        restoreLocally(p)
        viewModelScope.launch {
            // 等删除请求落定，避免 PUT 抢在 DELETE 前面被随后删除
            deleteJob?.join()
            val item = p.item
            try {
                runner.call {
                    api.putProgress(
                        PutProgressBody(
                            comicId = item.id,
                            chapterId = item.chapterId,
                            chapterIndex = item.chapterIndex,
                            pageIndex = item.pageIndex,
                            pageCount = item.progressPageCount.coerceAtLeast(1),
                            percent = item.progress?.percent ?: 0.0,
                            updatedAt = p.updatedAt.takeIf { it > 0 },
                        ),
                    )
                }
                _messages.send(HistoryMessage.Toast("阅读记录已恢复", ToastKind.SUCCESS))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(
                        items = s.items.filterNot { it.id == item.id },
                        total = (s.total - 1).coerceAtLeast(0),
                    )
                }
                // Web 用的是固定文案，不把接口错误直接抛给用户（那本书确实可能已被移出库）
                _messages.send(
                    HistoryMessage.Toast("恢复失败，可能这部漫画已被移除", ToastKind.ERROR),
                )
            }
        }
    }

    private fun restoreLocally(p: PendingDelete) {
        _state.update { s ->
            if (s.items.any { it.id == p.item.id }) {
                s
            } else {
                val index = p.index.coerceIn(0, s.items.size)
                s.copy(
                    items = s.items.toMutableList().apply { add(index, p.item) },
                    total = s.total + 1,
                )
            }
        }
    }

    private fun fetch(reset: Boolean, pullToRefresh: Boolean, limit: Int = PAGE_SIZE) {
        if (reset) loadToken++
        val token = loadToken
        val offset = if (reset) 0 else _state.value.items.size
        _state.update {
            if (reset) {
                it.copy(
                    loading = it.items.isEmpty() && !pullToRefresh,
                    refreshing = pullToRefresh,
                    error = null,
                    loadMoreFailed = false,
                )
            } else {
                it.copy(loadingMore = true, loadMoreFailed = false)
            }
        }
        viewModelScope.launch {
            try {
                val res = runner.call { api.history(limit = limit, offset = offset) }
                if (token != loadToken) return@launch
                _state.update { s ->
                    val merged = if (reset) {
                        res.items
                    } else {
                        val known = s.items.mapTo(HashSet()) { it.id }
                        s.items + res.items.filter { it.id !in known }
                    }
                    s.copy(
                        items = merged,
                        total = res.total,
                        hasMore = merged.size < res.total,
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = null,
                        loadMoreFailed = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token != loadToken) return@launch
                val message = e.message ?: "加载失败"
                _state.update {
                    if (reset) it.copy(loading = false, refreshing = false, error = message)
                    else it.copy(loadingMore = false, loadMoreFailed = true)
                }
                if (_state.value.items.isNotEmpty()) _messages.send(HistoryMessage.Toast(message, ToastKind.ERROR))
            }
        }
    }
}
