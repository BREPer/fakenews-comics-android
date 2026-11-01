package com.fakenews.comics.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.model.ComicDto
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.net.ImageUrls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 与列表接口同一个分页大小（Web `LIST_PAGE_SIZE`）。 */
/** 与列表接口同一个分页大小（Web `LIST_PAGE_SIZE`）；书架尾部「已经到底了」也要用它判断 */
internal const val PAGE_SIZE = 36

/** 输入防抖（Web `Search.tsx` 的 300ms）。 */
private const val DEBOUNCE_MS = 300L

/**
 * 「还没有任何结果可展示」时，等多久才换成骨架屏（Web `SEARCH_SLOW_MS`）。
 *
 * 本地库一次搜索约 30ms：一上来就换骨架，那帧灰块只闪两帧，比不闪还难看，
 * 而且骨架比空态高，页面会撑开再缩回。200ms 是「慢网（NAS 唤醒）该给信号」与
 * 「快搜索不该闪」之间的分界 —— 实测慢网下列表可见时长 760~919ms，远在此之后。
 */
private const val SLOW_MS = 200L

@Immutable // 让 Lazy item 的 content lambda 可被 memoize（理由见 data/model/ComicDtos.kt）
data class SearchUiState(
    /** 输入框里的原始文字（打字即显示，不等防抖） */
    val text: String = "",
    /** 防抖后真正发出去的关键词；为空表示没在搜 */
    val q: String = "",
    val items: List<ComicDto> = emptyList(),
    val total: Int = 0,
    /** 有请求在飞（只含真正发出去的请求，不含防抖那 300ms） */
    val fetching: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreFailed: Boolean = false,
    val hasMore: Boolean = false,
    /** 这次搜索已经慢到值得换骨架屏了 */
    val slow: Boolean = false,
) {
    /** 有关键词、一个结果都没有、且请求在飞 —— Web 的 `pendingFirst` */
    val pendingFirst: Boolean get() = q.isNotBlank() && items.isEmpty() && fetching
}

/**
 * 搜索页状态机（对齐 Web `pages/Search.tsx`）。
 *
 * 三件事不能省，都是 Web 上踩过的坑：
 *  1. **防抖 300ms**：每敲一个字都发请求，慢网下会把 NAS 打满；
 *  2. **转圈只在真正发请求时出现**：把防抖那 300ms 也算进去的话，每敲一个字都闪一下，
 *     看起来像故障；
 *  3. **慢查询 200ms 才换骨架**：见 [SLOW_MS]。
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: FakenewsApi,
    private val runner: ApiRunner,
    private val imageUrls: ImageUrls,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var debounceJob: Job? = null
    private var slowJob: Job? = null
    private var loadToken = 0
    private var page = 0

    fun coverUrl(comicId: String): String? = imageUrls.cover(comicId, 320)

    fun onTextChange(value: String) {
        _state.update { it.copy(text = value) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            applyQuery(value.trim())
        }
    }

    /**
     * 软键盘上的「搜索」键：立刻用当前文字发一次，不再等防抖。
     *
     * Web 没有这个动作（输入即防抖搜索），但 Android 的 IME 一定要有回执 ——
     * 按了搜索键却还要干等 300ms，会被当成没响应。
     */
    fun submitNow() {
        debounceJob?.cancel()
        applyQuery(_state.value.text.trim())
    }

    private fun applyQuery(q: String) {
        val before = _state.value.q
        if (q == before) {
            // 关键词没变（例如只是补了个空格）：不发请求，但要把「正在转圈」复位
            if (_state.value.fetching) _state.update { it.copy(fetching = false) }
            return
        }
        // 换关键词：旧结果必须丢掉，否则「搜 A 的结果」会先显示在「搜 B」的标题下
        loadToken++
        page = 0
        _state.update {
            it.copy(
                q = q,
                items = emptyList(),
                total = 0,
                hasMore = false,
                error = null,
                loadMoreFailed = false,
                fetching = q.isNotEmpty(),
                slow = false,
            )
        }
        slowJob?.cancel()
        if (q.isEmpty()) return
        fetch(reset = true)
    }

    /** 输入框右侧的 × ：清空并立即回到「输入关键词开始搜索」。 */
    fun clear() {
        debounceJob?.cancel()
        slowJob?.cancel()
        loadToken++
        page = 0
        _state.update {
            SearchUiState(text = "", q = "", fetching = false, slow = false)
        }
    }

    fun retry() {
        if (_state.value.q.isBlank()) return
        _state.update { it.copy(error = null, fetching = true, slow = false) }
        fetch(reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.q.isBlank() || s.fetching || s.loadingMore || !s.hasMore || s.loadMoreFailed) return
        fetch(reset = false)
    }

    fun retryLoadMore() {
        _state.update { it.copy(loadMoreFailed = false) }
        loadMore()
    }

    /**
     * 慢查询计时。只在「有关键词 + 无结果 + 请求在飞」时启动，
     * 一旦这个前提不成立就立刻取消并复位 —— 否则下一次快搜索会带着慢的标志进场。
     */
    private fun armSlowTimer() {
        val s = _state.value
        val pending = s.q.isNotBlank() && s.items.isEmpty() && s.fetching
        if (!pending) {
            slowJob?.cancel()
            if (s.slow) _state.update { it.copy(slow = false) }
            return
        }
        if (slowJob?.isActive == true) return
        slowJob = viewModelScope.launch {
            delay(SLOW_MS)
            _state.update { it.copy(slow = true) }
        }
    }

    private fun fetch(reset: Boolean) {
        if (reset) loadToken++
        val token = loadToken
        val q = _state.value.q
        if (q.isBlank()) return
        val nextPage = if (reset) 1 else page + 1
        _state.update {
            if (reset) it.copy(fetching = true, error = null, loadMoreFailed = false)
            else it.copy(loadingMore = true, loadMoreFailed = false)
        }
        armSlowTimer()
        viewModelScope.launch {
            try {
                val res = runner.call {
                    api.listComics(
                        q = q,
                        page = nextPage,
                        pageSize = PAGE_SIZE,
                    )
                }
                if (token != loadToken) return@launch
                page = nextPage
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
                        hasMore = res.hasMore,
                        fetching = false,
                        loadingMore = false,
                        error = null,
                    )
                }
                armSlowTimer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token != loadToken) return@launch
                _state.update {
                    if (reset) it.copy(fetching = false, error = e.message ?: "搜索失败", slow = false)
                    else it.copy(loadingMore = false, loadMoreFailed = true)
                }
                slowJob?.cancel()
            }
        }
    }
}
