package com.fakenews.comics.ui.reader

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.model.ChapterDto
import com.fakenews.comics.data.model.PageMetaDto
import com.fakenews.comics.data.model.PutProgressBody
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.net.ImageUrls
import com.fakenews.comics.data.reader.ChapterPrefetcher
import com.fakenews.comics.data.reader.PagePreloadPool
import com.fakenews.comics.data.session.ChapterView
import com.fakenews.comics.data.session.ComicEvents
import com.fakenews.comics.data.session.ReaderMode
import com.fakenews.comics.data.session.ReaderPrefs
import com.fakenews.comics.data.session.ReaderSettings
import com.fakenews.comics.data.session.ViewPrefs
import com.fakenews.comics.ui.common.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

private const val REPORT_DEBOUNCE_MS = 1_000L

/** 距边缘多少页开始预热相邻章节，与 Web 端 `Reader.tsx` 的 NEAR 一致 */
private const val PREFETCH_NEAR = 3

/** 延迟多久发预取：别和当前页的渲染/解码抢带宽 */
private const val PREFETCH_DELAY_MS = 300L

/** 阅读器居中胶囊提示（见 `_notices`）。[dir] 决定从哪一侧浮入。 */
data class ReaderNotice(val text: String, val dir: Int = 0)

@Immutable // 让 Lazy item 的 content lambda 可被 memoize（理由见 data/model/ComicDtos.kt）
data class ReaderUiState(
    val loading: Boolean = true,
    /** 切章中：保留上一页，只盖一层转圈，避免整屏闪黑 */
    val switching: Boolean = false,
    val error: String? = null,
    val comicTitle: String = "",
    val chapters: List<ChapterDto> = emptyList(),
    val chapterIndex: Int = -1,
    val pages: List<PageMetaDto> = emptyList(),
    /** **模式页**索引（双页 2 图 1 页、拆分 1 图 2 页），不是图索引 */
    val pageIndex: Int = 0,
    /** 双页对开内的偏移：恢复进度时记住读的是对开里的第几张 */
    val doubleSub: Int = 0,
    val settings: ReaderSettings = ReaderSettings(),
    /** 章节抽屉排版：列表 / 四格（本机记忆，与详情页分开存） */
    val chapterView: ChapterView = ChapterView.LIST,
) {
    val chapter: ChapterDto? get() = chapters.getOrNull(chapterIndex)

    /** 本章的图数 */
    val pageCount: Int get() = pages.size

    /** 本章按当前模式折算出的页数（页码、滑块、越界判定都用它） */
    val modePageCount: Int get() = modePageCount(settings.mode, pages.size)

    /** 上报/续读/预加载用的图索引 */
    val imageIndex: Int
        get() = imageIndexOf(settings.mode, pageIndex, doubleSub, pages.size)
}

/** 一次进度写入需要的全部信息，在触发点取值，避免异步执行时读到已经切换过的章节。 */
private data class ReportTarget(
    val signature: String,
    val chapterId: String,
    val chapterIndex: Int,
    val imageIndex: Int,
    val imageCount: Int,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val api: FakenewsApi,
    private val runner: ApiRunner,
    private val imageUrls: ImageUrls,
    private val events: ComicEvents,
    private val prefs: ReaderPrefs,
    private val prefetcher: ChapterPrefetcher,
    private val preloadPool: PagePreloadPool,
    private val viewPrefs: ViewPrefs,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val comicId: String = savedStateHandle.get<String>(ARG_COMIC).orEmpty()
    private val startChapterId: String = savedStateHandle.get<String>(ARG_CHAPTER).orEmpty()

    /** `page` 为 -1 表示「按服务端进度续读」，与 0（确实在第一页）要区分开；值是**图索引**。 */
    private val startPageArg: Int = savedStateHandle.get<Int>(ARG_PAGE) ?: -1

    private val _state = MutableStateFlow(
        ReaderUiState(settings = prefs.settings, chapterView = viewPrefs.readerChapterView),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /**
     * 阅读器轻提示（Web 的 `reader-notice`：**居中玻璃胶囊**）。
     *
     * 与 [_messages] 的分工不能混：这条是「阅读流里的瞬时反馈」（到头了 / 换章了），
     * 出现在**画面中央**、1.5s、无按钮；toast 在底部，用于「保存失败」这类系统回执。
     * 早先两句到头提示走的是 toast，与 Web 的居中胶囊不是同一种语言。
     *
     * `dir`：-1 = 往上一侧浮入（到头了「已经第一页」）、+1 = 往下一侧、0 = 中性（换章）。
     */
    private val _notices = Channel<ReaderNotice>(Channel.CONFLATED)
    val notices = _notices.receiveAsFlow()

    private var loadToken = 0
    private var reportJob: Job? = null
    private var prefetchJob: Job? = null

    /** 本章是否发生过用户主动翻页：预热上一章时要用它排除「刚进来就停在章首」 */
    private var turnedFor = ""

    /** 已上报的位置，用来去掉「打开就在第一页」这种没有实际翻页的上报。 */
    private var lastSent = ""
    private var started = false

    /** 串行化进度写：切章时新章的上报不能抢在上一章落定之前（服务端最后写入者获胜）。 */
    private val reportMutex = Mutex()

    /** viewModelScope 会随退出一起取消，最后这次上报得活过清理，否则「翻完就走」会丢页码。 */
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (started) return
        started = true
        val token = ++loadToken
        viewModelScope.launch {
            /*
             * 详情与页清单**并发**发出。
             *
             * 两者互不依赖：页清单只要 comicId + chapterId（路由里就有），详情只提供章节表、
             * 标题与续读进度。早先是严格串行 —— 先等详情返回，再拿它的章节去请求页清单，
             * 打开一次阅读器的等待时间等于**两个往返相加**。局域网里这也许只是几十毫秒，
             * 但 NAS 上「读目录 + 序列化几百章 JSON」并不便宜，而这段时间用户对着的
             * 是一块黑屏加一颗转圈。
             *
             * 并发之后总延迟从「和」变成「最大值」，且首张图能更早开始下载/解码。
             *
             * 两个请求都包成 [attempt] 而不是直接 await 抛异常：`async` 里抛出的异常会
             * **顺着父子关系取消整个作用域**，于是「页清单先失败」会连带把详情也取消掉，
             * 接着 `detailTask.await()` 抛 CancellationException 一路穿出去 —— 界面就
             * 永远停在 loading 的黑屏上。把失败收进 Result，等待顺序就与失败顺序无关了。
             */
            val detailTask = async { attempt { runner.call { api.comicDetail(comicId) } } }
            val pagesTask = async {
                attempt { runner.call { api.chapterPages(comicId, startChapterId) }.pages }
            }

            val detailRes = detailTask.await()
            val detail = detailRes.getOrNull()
            if (detail == null) {
                // 详情决定「章节是否存在 / 续读到哪一页」，它失败就无从谈起；顺手取消在飞的页清单
                pagesTask.cancel()
                val e = detailRes.exceptionOrNull()
                _state.update { it.copy(loading = false, error = e?.message ?: "加载失败") }
                return@launch
            }
            if (token != loadToken) return@launch

            val index = detail.chapters.indexOfFirst { it.id == startChapterId }
            if (index < 0) {
                pagesTask.cancel()
                _state.update {
                    it.copy(loading = false, error = "章节不存在，可能已被移除或重新扫描过")
                }
                return@launch
            }
            _state.update { it.copy(comicTitle = detail.title, chapters = detail.chapters) }

            val resume = detail.progress
                ?.takeIf { it.chapterId == startChapterId }
                ?.pageIndex
                ?: 0
            val target = if (startPageArg >= 0) startPageArg else resume
            // 通常在这里只需等一下已经在飞的请求（上面等详情的那段时间它早就到了）。
            // 这里**不走 prefetcher 的页清单缓存**：首开几乎不可能命中（预取只覆盖相邻章，
            // 而离开阅读器时缓存已清），绕开它换来的是请求能在选章节的同一刻就发出去。
            val pagesRes = pagesTask.await()
            if (token != loadToken) return@launch
            val pages = pagesRes.getOrNull()
            if (pages == null) {
                fail(pagesRes.exceptionOrNull()?.message ?: "页面加载失败", first = true)
                return@launch
            }
            installPages(index, target, pages, first = true)
        }
    }

    /**
     * 把一次请求收成 Result。`runCatching` 不能用：它连 CancellationException 一起吞，
     * 退出阅读器时的正常取消会被当成「加载失败」闪一下错误态。
     */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun retry() {
        started = false
        _state.update { ReaderUiState(settings = it.settings) }
        start()
    }

    fun pageUrl(index: Int): String? {
        val chapter = _state.value.chapter ?: return null
        return imageUrls.page(comicId, chapter.id, index, _state.value.settings.quality)
    }

    val canGoPreviousChapter: Boolean get() = _state.value.chapterIndex > 0

    val canGoNextChapter: Boolean get() = _state.value.chapterIndex in 0 until _state.value.chapters.size - 1

    /** 由翻页容器回调（**模式页**）：只认已经落定的页。 */
    fun observePage(page: Int) {
        val s = _state.value
        if (s.pages.isEmpty()) return
        val target = page.coerceIn(0, s.modePageCount - 1)
        if (target == s.pageIndex && s.doubleSub == 0) return
        val chapterId = s.chapter?.id.orEmpty()
        // 整对开翻页：新对开从第一张读起，不能把上一对开记下的右页偏移带过去
        _state.update { it.copy(pageIndex = target, doubleSub = 0) }
        turnedFor = chapterId
        scheduleReport()
        schedulePrefetch()
    }

    /** 进度条拖动 / 章节抽屉跳转（模式页）。 */
    fun seek(page: Int) = observePage(page)

    /** 点击左右三成：+1 = 点右侧。朝向由 VM 按阅读方向换算。 */
    fun turn(delta: Int) {
        val s = _state.value
        if (s.pages.isEmpty()) return
        val step = if (s.settings.rtl) -delta else delta
        val target = s.pageIndex + step
        when {
            target in 0..s.modePageCount - 1 -> observePage(target)
            step > 0 -> crossChapter(next = true)
            else -> crossChapter(next = false)
        }
    }

    /** 左右滑动的方向（屏幕方向，不含阅读方向）：拖动/平移轨道直接给索引增量。 */
    fun turnByIndex(delta: Int) {
        val s = _state.value
        if (s.pages.isEmpty()) return
        val target = s.pageIndex + delta
        when {
            target in 0..s.modePageCount - 1 -> observePage(target)
            else -> crossChapter(next = delta > 0)
        }
    }

    /**
     * 越过首/末页继续翻 = 换章：往前从第 0 页开始，往回落到上一章末页。
     * 返回是否真的换了章（平移轨道要用它决定滑出去还是弹回来）。
     */
    fun crossChapter(next: Boolean): Boolean {
        val s = _state.value
        val target = if (next) s.chapterIndex + 1 else s.chapterIndex - 1
        val chapter = s.chapters.getOrNull(target)
        if (chapter == null) {
            _notices.trySend(
                ReaderNotice(
                    text = if (next) "已经是最后一页了" else "已经是第一页了",
                    dir = if (next) 1 else -1,
                ),
            )
            return false
        }
        openChapter(target, if (next) 0 else (chapter.pageCount - 1).coerceAtLeast(0))
        // Web `ReaderShell.tsx:177/183`：换章成功也要说一句，否则画面一变用户不知道发生了什么
        _notices.trySend(ReaderNotice(if (next) "进入「${chapter.title}」" else "回到「${chapter.title}」"))
        return true
    }

    /** [image] 是图索引：与 Web 端 onChapterChange 的口径一致。 */
    fun openChapter(index: Int, image: Int = 0) {
        val s = _state.value
        val chapter = s.chapters.getOrNull(index) ?: return
        val img = image.coerceIn(0, (chapter.pageCount - 1).coerceAtLeast(0))
        if (index == s.chapterIndex && s.pages.isNotEmpty() && img == s.imageIndex) return
        flushReport()
        loadPages(index, img, first = false)
    }

    /** 设置面板：全部写本机（与 Web 端一致），换模式时把当前图索引换算到新页码域。 */
    /** 章节抽屉排版切换：先落盘再改状态 */
    fun setChapterView(view: ChapterView) {
        viewPrefs.readerChapterView = view
        _state.update { it.copy(chapterView = view) }
    }

    fun updateSettings(next: ReaderSettings) {
        val s = _state.value
        prefs.settings = next
        if (next.mode == s.settings.mode || s.pages.isEmpty()) {
            _state.update { it.copy(settings = next) }
            return
        }
        val img = s.imageIndex
        _state.update {
            it.copy(
                settings = next,
                pageIndex = imageToModePage(next.mode, img, it.pages.size),
                // 偏移不是图索引的奇偶：首页单占之后对开起点可能是奇数张
                doubleSub = if (next.mode == ReaderMode.DOUBLE) doubleSubOffset(img, it.pages.size) else 0,
            )
        }
    }

    /** 翻页/切后台/退出前把最后的页码落定。 */
    fun flushReport() {
        reportJob?.cancel()
        sendReportNow()
    }

    /**
     * 把当前页前后的若干张图真正解码好，翻页时直接从内存出。
     *
     * 与 Web 端 `usePreloader` 同范围内的三件事：当前页（续读进来时它不在「后 N 页」里）、
     * 后 [count] 页、往回一页。preload=0 时什么都不做——那是「关」档位。
     */
    fun preloadPages(imageIndex: Int, count: Int) {
        val s = _state.value
        if (s.pageCount == 0 || count <= 0) return
        val cur = imageIndex.coerceIn(0, s.pageCount - 1)
        val targets = buildList {
            add(cur)
            for (i in cur + 1..minOf(s.pageCount - 1, cur + count)) add(i)
            if (cur > 0) add(cur - 1)
        }
        targets
            .mapNotNull { index -> pageUrl(index) }
            .forEach { url ->
                viewModelScope.launch(Dispatchers.IO) { runCatching { preloadPool.preload(url) } }
            }
    }

    override fun onCleared() {
        flushReport()
        // 离开阅读器就丢掉预热成果：页清单常驻几十 KB 无所谓，
        // 但解码位图一定要放，否则这些 SA strong 引用会把内存一直摁住
        prefetchJob?.cancel()
        prefetcher.clear()
        preloadPool.release()
    }

    /** 调用方可能已经不在 ViewModel 的作用域里，所以自己起一个能活过清理的协程。 */
    private fun sendReportNow() {
        val target = currentReport() ?: return
        flushScope.launch { sendReport(target) }
    }

    private fun scheduleReport() {
        reportJob?.cancel()
        reportJob = viewModelScope.launch {
            delay(REPORT_DEBOUNCE_MS)
            sendReport(currentReport() ?: return@launch)
        }
    }

    /** 在调用点就把页码钉死：异步协程再去读 state，切章后拿到的是新章的页码。 */
    private fun currentReport(): ReportTarget? {
        val s = _state.value
        val chapter = s.chapter ?: return null
        if (s.pages.isEmpty()) return null
        val img = s.imageIndex
        return ReportTarget(
            signature = "${s.chapterIndex}|$img",
            chapterId = chapter.id,
            chapterIndex = chapter.index,
            imageIndex = img,
            imageCount = s.pageCount,
        )
    }

    private suspend fun sendReport(target: ReportTarget) {
        if (target.signature == lastSent) return
        lastSent = target.signature
        reportMutex.withLock {
            try {
                val res = runner.call {
                    api.putProgress(
                        PutProgressBody(
                            comicId = comicId,
                            chapterId = target.chapterId,
                            chapterIndex = target.chapterIndex,
                            pageIndex = target.imageIndex,
                            pageCount = target.imageCount,
                            // 章内百分比：服务端会按全书重算，这里只当兜底值
                            percent = (target.imageIndex + 1.0) / target.imageCount * 100.0,
                        ),
                    )
                }
                events.emit(
                    ComicEvents.Event.ProgressChanged(
                        comicId = comicId,
                        percent = res.percent,
                        chapterId = target.chapterId,
                        chapterIndex = target.chapterIndex,
                        pageIndex = target.imageIndex,
                        pageCount = target.imageCount,
                    ),
                )
            } catch (e: CancellationException) {
                lastSent = ""
                throw e
            } catch (e: Exception) {
                // 允许下一次翻页重试，否则整章都再也存不进进度
                lastSent = ""
                _messages.send(UiMessage.err("进度保存失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    /**
     * 章末/章首提前预热相邻章节（对应 Web 端 `Reader.tsx` 的相邻章节预取 effect）。
     *
     * 只在位置的**上游**一次 [PREFETCH_DELAY_MS] 后发：入口附近的位置变化会连着触发
     * 好几次，effect 语义本就自带延迟。上一次没跑的任务直接取消——它拿到的位置已经过期。
     */
    private fun schedulePrefetch() {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(PREFETCH_DELAY_MS)
            prefetchNeighbors()
        }
    }

    private suspend fun prefetchNeighbors() {
        val s = _state.value
        if (s.pages.isEmpty()) return
        val chapter = s.chapter ?: return
        val maxPage = maxOf(0, s.modePageCount - 1)
        val quality = s.settings.quality
        val next = s.chapters.getOrNull(s.chapterIndex + 1)
        if (next != null && s.pageIndex >= maxPage - PREFETCH_NEAR) {
            runCatching { prefetcher.prefetch(comicId, next, quality, fromStart = true) }
        }
        val prev = s.chapters.getOrNull(s.chapterIndex - 1)
        // 往回翻才预热上一章：刚打开就停在章首（续读、从目录进来）时预热上一章是白烧编码，
        // 还会和本章首屏那几张图抢服务端 CPU
        if (prev != null && s.pageIndex <= PREFETCH_NEAR && turnedFor == chapter.id) {
            runCatching { prefetcher.prefetch(comicId, prev, quality, fromStart = false) }
        }
    }

    private suspend fun fetchChapterPages(chapter: ChapterDto): List<PageMetaDto> {
        // 预取过的章节直接用内存里的页清单渲染：换章不再等一次 JSON 往返。
        // 页数与章节元数据不一致时 cachedPages 返回 null（重新扫描会改页数），退回正常加载。
        prefetcher.cachedPages(comicId, chapter.id, chapter.pageCount)?.let { return it }
        return runner.call { api.chapterPages(comicId, chapter.id) }.pages
    }

    /**
     * 页清单到手后落状态。首帧（[start]）与换章（[loadPages]）共用，免得两处各写一份
     * ——页码换算与「本次位置算不算翻页」的判定一旦分叉，续读记录就会出错。
     */
    private fun installPages(index: Int, image: Int, res: List<PageMetaDto>, first: Boolean) {
        if (res.isEmpty()) {
            fail("这一章没有可显示的页面", first)
            return
        }
        val img = image.coerceIn(0, res.lastIndex)
        val mode = _state.value.settings.mode
        _state.update {
            it.copy(
                loading = false,
                switching = false,
                error = null,
                chapterIndex = index,
                pages = res,
                pageIndex = imageToModePage(mode, img, res.size),
                doubleSub = if (mode == ReaderMode.DOUBLE) doubleSubOffset(img, res.size) else 0,
            )
        }
        // 打开时的位置不算「翻页」，否则只是点开看一眼就会把阅读记录顶到最前
        if (first) lastSent = "$index|$img" else scheduleReport()
        // 页清单刚到位等价于「位置变了」：例如续读直接落在末几页，此时就该预热下一章
        schedulePrefetch()
    }

    private fun loadPages(index: Int, image: Int, first: Boolean) {
        val chapter = _state.value.chapters.getOrNull(index) ?: return
        val token = ++loadToken
        _state.update {
            it.copy(
                loading = first,
                switching = !first,
                error = if (first) null else it.error,
            )
        }
        viewModelScope.launch {
            try {
                val res = fetchChapterPages(chapter)
                if (token != loadToken) return@launch
                installPages(index, image, res, first)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token != loadToken) return@launch
                fail(e.message ?: "页面加载失败", first)
            }
        }
    }

    /** 切章失败留着上一页比整屏错误态有用，所以只在首章进错误态，其余走提示。 */
    private fun fail(message: String, first: Boolean) {
        if (first) {
            _state.update { it.copy(loading = false, switching = false, error = message) }
        } else {
            _state.update { it.copy(switching = false) }
            _messages.trySend(UiMessage.err(message))
        }
    }

    companion object {
        const val ARG_COMIC = "comicId"
        const val ARG_CHAPTER = "chapterId"
        const val ARG_PAGE = "page"
    }
}
