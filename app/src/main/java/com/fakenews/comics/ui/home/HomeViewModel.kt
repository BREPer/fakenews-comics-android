package com.fakenews.comics.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.model.CONTINUE_FETCH_SIZE
import com.fakenews.comics.data.model.ComicDto
import com.fakenews.comics.data.model.FacetsDto
import com.fakenews.comics.data.model.HistoryItemDto
import com.fakenews.comics.data.model.ScanStatusDto
import com.fakenews.comics.data.model.continueReadingOf
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.net.ImageUrls
import com.fakenews.comics.data.session.ComicEvents
import com.fakenews.comics.data.session.progressOf
import com.fakenews.comics.data.session.withMeta
import com.fakenews.comics.data.session.withProgress
import com.fakenews.comics.ui.common.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 与列表接口同一个分页大小（Web `LIST_PAGE_SIZE`）；页脚用它判断「到底了」是否值得展示 */
internal const val PAGE_SIZE = 36

/** 扫描进行中的轮询间隔：足够让进度条动起来，又不至于把 NAS 打满（Web 同值） */
private const val SCAN_ACTIVE_MS = 800L

/** 扫描空闲时的轮询间隔（Web 同值）：只为发现「别人从 Web 端点了扫描」 */
private const val SCAN_IDLE_MS = 15_000L

/**
 * 首页筛选（对齐 Web 首页的 URL query 四项）。
 *
 * 关键词不在这里：Web 的搜索是独立页 `/search`（`?q=`），首页只负责
 * 「状态 / 格式 / 标签 / 排序」。以前 Android 把 q 也塞进来，导致首页顶着
 * 一个搜索框、却和搜出来的结果混在同一份状态里。
 */
data class HomeFilter(
    val status: String? = null,
    val format: String? = null,
    val tag: String? = null,
    val sort: String = "addedAt",
)

@Immutable // 让 Lazy item 的 content lambda 可被 memoize（理由见 data/model/ComicDtos.kt）
data class HomeUiState(
    val filter: HomeFilter = HomeFilter(),
    val facets: FacetsDto? = null,
    val items: List<ComicDto> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreFailed: Boolean = false,
    val hasMore: Boolean = false,
    /**
     * 顶部「继续阅读」横条的数据。它是辅助信息：拉不到就不显示（不打断列表），
     * 所以失败时没有 error 字段——要么有内容，要么整块不出现。
     * [continueLoading] 只服务骨架屏：请求在飞期间先占位，避免数据到达时
     * 把下面的筛选 chips 整块顶下去（Web 首页同款 CLS 处理）。
     */
    val continueItems: List<HistoryItemDto> = emptyList(),
    val continueLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: FakenewsApi,
    private val runner: ApiRunner,
    private val imageUrls: ImageUrls,
    private val events: ComicEvents,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * 扫描状态（[ScanStatusDto]）**单独一条流**，不放进 [HomeUiState]。
     *
     * 扫描是唯一的长时间操作，期间书架原本毫无提示：列表是空的，用户看到
     * 「还没有漫画」，很自然会怀疑是导入失败。Web 首页常驻这个观察器
     * （`useScanWatch(true, 15_000)`），这里对齐。
     *
     * 为什么不塞进 HomeUiState：扫描进行中 `done` 每 800ms 变一次，混在同一个
     * state 里会让整页（筛选项、30 个 chip、可见卡片）每 800ms 全部重组一遍。
     * 拆开之后扫描期间只有读这条流的那一小块（扫描横幅 / 空态）会重绘。
     */
    private val _scan = MutableStateFlow<ScanStatusDto?>(null)
    val scan: StateFlow<ScanStatusDto?> = _scan.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var page = 0
    private var loadToken = 0

    /** 扫描观察器（见 [startScanWatch]），由界面在 RESUMED 期间持有 */
    private var watchJob: Job? = null

    init {
        fetch(reset = true, pullToRefresh = false)
        loadFacets()
        loadContinueReading()
        // 扫描观察器不在这里启动：它由界面在 RESUMED 期间驱动（见 startScanWatch）
        viewModelScope.launch {
            events.events.collect { event ->
                when (event) {
                    is ComicEvents.Event.FavoriteChanged -> _state.update { s ->
                        s.copy(
                            items = s.items.map {
                                if (it.id == event.comicId) it.copy(isFavorite = event.favorite) else it
                            },
                        )
                    }
                    // 库被新建/改路径/删除：这一页的快照整个失效（删库会级联清条目），
                    // 且没有 comicId 可打补丁 —— 整页重取是唯一解。
                    ComicEvents.Event.LibraryChanged -> {
                        fetch(reset = true, pullToRefresh = false)
                        loadFacets()
                        loadContinueReading()
                    }
                    is ComicEvents.Event.ProgressCleared -> _state.update { s ->
                        s.copy(
                            items = s.items.map {
                                if (it.id == event.comicId) {
                                    it.copy(progress = null, readStatus = "unread", lastReadAt = null)
                                } else {
                                    it
                                }
                            },
                            continueItems = s.continueItems.filterNot { it.id == event.comicId },
                        )
                    }
                    // 阅读器翻页后回到书架：卡片进度条要接着走，不该等下一次下拉刷新
                    is ComicEvents.Event.ProgressChanged -> {
                        val inContinue = _state.value.continueItems.any { it.id == event.comicId }
                        _state.update { s ->
                            s.copy(
                                items = s.items.map {
                                    if (it.id != event.comicId) it else it.withProgress(event)
                                },
                                continueItems = patchContinue(s.continueItems, event),
                            )
                        }
                        // 第一次读某本书时它还不在横条里，就地补不出一行（缺标题等字段），
                        // 只能重取一次。之后这本书再翻页就走上面的就地打补丁，不会再发请求。
                        if (!inContinue && event.percent < 100.0) loadContinueReading()
                    }

                    // 详情页改了标题/作者：卡片不该还显示旧名字
                    is ComicEvents.Event.MetaChanged -> _state.update { s ->
                        s.copy(
                            items = s.items.map {
                                if (it.id != event.comicId) it else it.withMeta(event)
                            },
                            continueItems = s.continueItems.map {
                                if (it.id != event.comicId) it else it.withMeta(event)
                            },
                        )
                    }
                }
            }
        }
    }

    fun coverUrl(comicId: String): String? = imageUrls.cover(comicId, 320)

    /**
     * 扫描观察器（对齐 Web `useScanWatch(true, 15_000)`）。
     *
     * 轮询节奏照抄 Web：进行中 800ms、空闲 15s。两个数字都不能随便调大 ——
     * 800ms 是「进度条动起来」的体感下限，15s 是空闲期的省电上限。
     *
     * 结束时做两件事（Web 在同一处做完）：
     *   1. 刷新列表与 facets —— 不刷的话首扫结束后书架还停在「还没有漫画」的旧结果上；
     *   2. 提示「扫描完成 · 新增 N 部」—— 扫描是唯一的长时间操作，结束时必须给闭环回执。
     *      N 用「扫描开始前记下的总数」比，而不是结束时现取：invalidate 之后的列表
     *      是异步到达的，立刻取还是旧值，delta 恒为 0（Web 的注释写了同一个坑）。
     *
     * **只在页面 RESUMED 期间跑**（界面调 [startScanWatch] / [stopScanWatch]）。
     * 早先它挂在 init 里常开：App 退到后台、或切到别的 tab，VM 还活着，就照样每 15s
     * 打一次 NAS。Web 的 `useScanWatch` 是组件挂载才观察、卸载就停 —— 这里对齐它。
     */
    fun startScanWatch() {
        if (watchJob?.isActive == true) return
        watchJob = viewModelScope.launch {
            var wasRunning = false
            var totalBefore = 0
            while (isActive) {
                val st = try {
                    runner.call { api.scanStatus() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null // 扫描状态是辅助信息：拿不到就当作没在扫，不打断书架
                }
                if (st == null) {
                    delay(SCAN_IDLE_MS)
                    continue
                }
                // 只有真正变了才写：StateFlow 会做相等性判断，但 ScanStatusDto 的
                // 进度字段每 800ms 都在变，这里省下的是「状态没变时的空转」
                if (_scan.value != st) _scan.value = st
                if (st.running) {
                    if (!wasRunning) {
                        wasRunning = true
                        totalBefore = _state.value.total
                    }
                } else if (wasRunning) {
                    wasRunning = false
                    onScanFinished(totalBefore)
                }
                delay(if (st.running) SCAN_ACTIVE_MS else SCAN_IDLE_MS)
            }
        }
    }

    /** 页面离开时停掉观察器（App 进后台、切 tab、进详情页都会走这里）。 */
    fun stopScanWatch() {
        watchJob?.cancel()
        watchJob = null
    }

    private suspend fun onScanFinished(totalBefore: Int) {
        // 等一拍：服务端写库与列表索引不是同一瞬间完成（Web 同样是 1.2s）
        delay(1200)
        val filtered = _state.value.filter.let {
            it.status != null || it.format != null || it.tag != null
        }
        // 只要 total，不要数据：pageSize=1 是最便宜的一次「数一下现在有多少本」
        val newTotal = try {
            runner.call {
                api.listComics(sort = "addedAt", order = "desc", page = 1, pageSize = 1)
            }.total
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        // 静默重取（不带下拉刷新的转圈）：扫描结束是后台事件，不该在网格上闪一下刷新指示
        loadFacets()
        fetch(reset = true, pullToRefresh = false)
        // totalBefore 为 0 说明扫描开始时列表还没加载完（冷启动就撞上扫描）：
        // 这时 newTotal 是全部藏书数，减 0 会报出「新增 509 部」这种荒唐数字，宁可不报数。
        val delta = if (newTotal != null && totalBefore > 0) newTotal - totalBefore else 0
        // 带筛选时总数不等于全库，报数字会误导 —— 这种情况只说「扫描完成」
        _messages.send(
            UiMessage.ok(if (delta > 0 && !filtered) "扫描完成 · 新增 $delta 部" else "扫描完成"),
        )
    }

    /**
     * 下拉手势专用。**必须**把 `refreshing` 立起来，哪怕列表是空的：
     * M3 的 `PullToRefreshElement` 是 data class，`isRefreshing` 没变化时它的
     * `update()` 根本不会被调用，而 `update()` 正是「指示器该收回去」的唯一入口 ——
     * 空列表下不置位，圆底就永远停在阈值处（实测：弧光像素数冻在 1487 不动）。
     */
    fun pullToRefresh() = reload(pullToRefresh = true)

    /** 错误态的「重试」：没有下拉手势要收尾，空列表照旧走骨架屏 */
    fun refresh() = reload(pullToRefresh = false)

    private fun reload(pullToRefresh: Boolean) {
        loadFacets()
        loadContinueReading()
        fetch(reset = true, pullToRefresh = pullToRefresh)
    }

    /**
     * 阅读器上报进度后横条就地更新：读完的移出（percent 是服务端全书值），
     * 还在读的更新落点并挪到最前——横条按「最近阅读」排序，刚读过的应该在第一个。
     */
    private fun patchContinue(
        items: List<HistoryItemDto>,
        event: ComicEvents.Event.ProgressChanged,
    ): List<HistoryItemDto> {
        val target = items.firstOrNull { it.id == event.comicId } ?: return items
        if (event.percent >= 100.0) return items.filterNot { it.id == event.comicId }
        val updated = target.copy(
            progress = progressOf(target.progress, event),
            chapterId = event.chapterId,
            chapterIndex = event.chapterIndex,
            pageIndex = event.pageIndex,
            progressPageCount = event.pageCount,
            lastReadAt = System.currentTimeMillis(),
        )
        return listOf(updated) + items.filterNot { it.id == event.comicId }
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

    fun toggleStatus(value: String) = applyFilter { f ->
        f.copy(status = if (f.status == value) null else value)
    }

    fun toggleFormat(value: String) = applyFilter { f ->
        f.copy(format = if (f.format == value) null else value)
    }

    fun toggleTag(value: String) = applyFilter { f ->
        f.copy(tag = if (f.tag == value) null else value)
    }

    fun setSort(value: String) = applyFilter { f -> f.copy(sort = value) }

    /** 「重置」= Web 的 `setParams(new URLSearchParams())`：连排序也回到默认的最近添加。 */
    fun clearFilters() = applyFilter { HomeFilter() }

    private fun applyFilter(change: (HomeFilter) -> HomeFilter) {
        val next = change(_state.value.filter)
        if (next == _state.value.filter) return
        _state.update {
            it.copy(filter = next, items = emptyList(), total = 0, hasMore = false, error = null)
        }
        page = 0
        fetch(reset = true, pullToRefresh = false)
    }

    private fun loadFacets() {
        viewModelScope.launch {
            try {
                val facets = runner.call { api.facets() }
                _state.update { it.copy(facets = facets) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 筛选 chips 是辅助信息：拉不到就不显示，不打断列表
            }
        }
    }

    /**
     * 顶部「继续阅读」横条。与 facets 同一策略：拉不到就整块不显示，绝不能用
     * 一个辅助请求的错误去打断下面的漫画列表。
     */
    private fun loadContinueReading() {
        viewModelScope.launch {
            try {
                val res = runner.call { api.history(limit = CONTINUE_FETCH_SIZE, offset = 0) }
                _state.update {
                    it.copy(continueItems = continueReadingOf(res.items), continueLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 静默：横条本来就是锦上添花，没有它书架照样能用
                _state.update { it.copy(continueLoading = false) }
            }
        }
    }

    private fun fetch(reset: Boolean, pullToRefresh: Boolean) {
        if (reset) loadToken++
        val token = loadToken
        val nextPage = if (reset) 1 else page + 1
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
            val f = _state.value.filter
            try {
                val res = runner.call {
                    api.listComics(
                        status = f.status,
                        format = f.format,
                        tag = f.tag,
                        sort = f.sort,
                        order = if (f.sort == "title") "asc" else "desc",
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
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = null,
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
                if (_state.value.items.isNotEmpty()) _messages.send(UiMessage.err(message))
            }
        }
    }
}
