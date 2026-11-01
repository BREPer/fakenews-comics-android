package com.fakenews.comics.ui.libraries

import androidx.compose.runtime.Immutable
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.model.LibraryBody
import com.fakenews.comics.data.model.LibraryBrowseDto
import com.fakenews.comics.data.model.LibraryDto
import com.fakenews.comics.data.model.LibraryPatchBody
import com.fakenews.comics.data.model.ScanBody
import com.fakenews.comics.data.model.ScanStatusDto
import com.fakenews.comics.data.model.UploadResultDto
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.session.ComicEvents
import com.fakenews.comics.data.upload.LibraryUploader
import com.fakenews.comics.data.upload.PickedFile
import com.fakenews.comics.ui.common.UiMessage
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

/**
 * 新建 / 编辑库的表单草稿。
 *
 * [paths] 是「每行一个目录」的原始文本（Web 的 textarea 同形态）：既能手输，
 * 也能被目录点选器整体覆盖。解析（去空行、去首尾空白）只在提交时做一次 ——
 * 边打字边解析会把「正在输入一半的路径」吃掉。
 */
data class LibraryDraft(
    val id: String? = null,
    val name: String = "",
    val type: String = "chapter",
    val paths: String = "",
)

/** 上传面板的状态；null = 面板关着。 */
data class UploadUiState(
    val libraryId: String,
    val libraryName: String,
    val isChapter: Boolean,
    val files: List<PickedFile> = emptyList(),
    /** 被扩展名过滤掉的文件名（与 Web 的 rejected 同一条信息，只是不再同份 CSS） */
    val rejected: List<String> = emptyList(),
    val uploading: Boolean = false,
    val sentBytes: Long = 0,
    val result: UploadResultDto? = null,
    val error: String? = null,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes.coerceAtLeast(0) }

    /** 0..1。大小未知（-1）的文件按 0 计入 total，分子可能先超过它，所以夹一下。 */
    val progress: Float
        get() {
            val total = totalBytes
            return if (total <= 0L) 0f else (sentBytes.toFloat() / total).coerceIn(0f, 1f)
        }
}

@Immutable // 让 Lazy item 的 content lambda 可被 memoize（理由见 data/model/ComicDtos.kt）
data class LibrariesUiState(
    val items: List<LibraryDto> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val scan: ScanStatusDto? = null,
    /** 触发扫描的请求在飞 */
    val scanBusy: Boolean = false,
    /** 新建 / 编辑的请求在飞（这两个都是 **suspend 调用**，成功与否由返回值告知界面） */
    val saving: Boolean = false,
    /** 正在删除的库 id */
    val removingId: String? = null,
    /** 上传面板 */
    val upload: UploadUiState? = null,
    // ---- 目录点选器 ----
    /** 浏览层级（第 0 层是挂载根）；空 = 面板没开 */
    val browseLevels: List<LibraryBrowseDto> = emptyList(),
    val browseLoading: Boolean = false,
    val browseError: String? = null,
    /** 已勾选的路径，顺序 = 勾选顺序 */
    val browseChecked: List<String> = emptyList(),
)

/**
 * 漫画库 —— 2026-10-28 起与 Web `pages/Libraries.tsx` **全量对齐**：
 * 看/扫之外，手机上同样能新建、改目录、上传、删除。
 *
 * 之前 Android 是只读的（理由是「选目录 / 传大文件属于桌面场景」），这个理由现在不成立了：
 * 我们加了目录点选器（`GET /api/libraries/browse`），路径不再需要手输 ——
 * 「填错路径导致书架为空」在 UI 层就消失了；上传走系统文件 picker + 流式 multipart，
 * 手机上一样稳。留着「只能看」反而成了凭空的缺陷，于是改成全量对齐。
 *
 * 有一处与列表页不同的口径：**增删改会同时广播 [ComicEvents.Event.LibraryChanged]**。
 * 首页/书架各自持有服务端快照，删掉一个库之后那些书还在快照里，不广播的话要等到
 * 下次扫描结束才消失（而删除并不触发扫描）。
 */
@HiltViewModel
class LibrariesViewModel @Inject constructor(
    private val api: FakenewsApi,
    private val runner: ApiRunner,
    private val uploader: LibraryUploader,
    private val events: ComicEvents,
) : ViewModel() {
    private val _state = MutableStateFlow(LibrariesUiState())
    val state: StateFlow<LibrariesUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var uploadJob: Job? = null

    init {
        refresh()
        refreshScan()
    }

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
                val res = runner.call { api.libraries() }
                _state.update {
                    it.copy(items = res.items, loading = false, refreshing = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, refreshing = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    /** 单次读取扫描状态。轮询节奏由界面决定（进行中 800ms、空闲 15s），VM 不做定时器。 */
    fun refreshScan() {
        viewModelScope.launch {
            try {
                val st = runner.call { api.scanStatus() }
                _state.update { it.copy(scan = st) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 扫描状态是辅助信息：拿不到就不显示进度，不打断库列表
            }
        }
    }

    /** 触发扫描。[libraryId] 为空即全库扫描；服务端说 `started=false` 表示已有扫描在跑。 */
    fun triggerScan(libraryId: String? = null) {
        if (_state.value.scanBusy) return
        _state.update { it.copy(scanBusy = true) }
        viewModelScope.launch {
            try {
                val res = runner.call { api.triggerScan(ScanBody(libraryId)) }
                _state.update { it.copy(scanBusy = false, scan = res.status ?: it.scan) }
                val name = libraryId?.let { id -> _state.value.items.firstOrNull { it.id == id }?.name }
                _messages.send(
                    when {
                        res.started && name != null -> UiMessage.ok("已开始扫描「$name」")
                        res.started -> UiMessage.ok("已开始扫描全部漫画库")
                        // 「没扫起来」不是失败（别人已经在扫了），用中性色——
                        // 打成红叉会让人以为出错了
                        else -> UiMessage.info("已有扫描在进行中，请稍候")
                    },
                )
                // 这里**不能**立刻 refresh：扫描才刚启动，comicCount 还是旧值，
                // 拿到的是同一份数据（早期版本就是这么写的，刷了等于没刷）。
                // 列表刷新交给界面：扫描结束（running true→false）时由轮询侧补一次。
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(scanBusy = false) }
                _messages.send(UiMessage.err("扫描启动失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    // ---- 新建 / 编辑 / 删除 ----

    /**
     * 保存。[draft.id] 为空即新建。
     *
     * 返回 **错误文案**（成功返回 null）：面板收不收由调用方决定，这样失败时
     * 表单原地留着、已输入的内容不丢。早期版本保存失败是「原地不动 + 一个字不提示」，
     * 用户只会反复点保存；这里失败还会额外走一条 toast（与 Web 同）。
     */
    suspend fun saveLibrary(draft: LibraryDraft): String? {
        val name = draft.name.trim()
        // 与 Web 同口径：按行切去首尾空白、丢空行
        val paths = draft.paths.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (name.isEmpty()) return "请填写漫画库名称"
        if (paths.isEmpty()) return "请至少选择一个目录路径"
        if (_state.value.saving) return null
        _state.update { it.copy(saving = true) }
        return try {
            val created = draft.id == null
            if (created) {
                runner.call { api.createLibrary(LibraryBody(name, draft.type, paths)) }
            } else {
                runner.call {
                    api.updateLibrary(
                        draft.id,
                        LibraryPatchBody(name = name, type = draft.type, paths = paths),
                    )
                }
            }
            // 服务端写完就开扫，这里同步刷新：**列表要立刻出现新库**，
            // comicCount 则由扫描结束时的那次补齐。
            refresh()
            refreshScan()
            events.emit(ComicEvents.Event.LibraryChanged)
            _messages.send(
                if (created) {
                    UiMessage.ok("漫画库已创建，正在扫描")
                } else {
                    UiMessage.ok("漫画库已更新")
                },
            )
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "${if (draft.id == null) "创建" else "保存"}失败：${e.message ?: "未知错误"}"
            _messages.send(UiMessage.err(msg))
            msg
        } finally {
            _state.update { it.copy(saving = false) }
        }
    }

    /**
     * 删除。不可撤销，所以回执里要把「删的是哪个、磁盘文件没动」说清楚
     * （Web `toast.success(\`已删除漫画库「${name}」（不删磁盘文件）\`)`）。
     *
     * 同样返回错误文案（成功 null）。失败原因**只**回给弹窗：这里是二次确认框，
     * 它自己开着，再叠一条 toast 是重复；沿用 Web ConfirmDialog 的 error 槽。
     */
    suspend fun deleteLibrary(id: String): String? {
        if (_state.value.removingId != null) return null
        val name = _state.value.items.firstOrNull { it.id == id }?.name
        _state.update { it.copy(removingId = id) }
        return try {
            val res = runner.call { api.deleteLibrary(id) }
            refresh()
            events.emit(ComicEvents.Event.LibraryChanged)
            val suffix = if (res.removedComics > 0) "，已移除 ${res.removedComics} 部条目" else ""
            _messages.send(
                if (name != null) {
                    UiMessage.ok("已删除漫画库「$name」（不删磁盘文件）$suffix")
                } else {
                    UiMessage.ok("漫画库已删除（不删磁盘文件）$suffix")
                },
            )
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "删除失败：${e.message ?: "未知错误"}"
        } finally {
            _state.update { it.copy(removingId = null) }
        }
    }

    // ---- 目录点选器 ----

    /**
     * 打开点选器：回到根并预勾 [initialPaths]。
     *
     * 每次都重置（不保留上次的深层浏览位置）：编辑 A 库时翻到第五层，关掉再编辑 B 库，
     * 留着旧位置会让人以为自己还在同一个库里。
     */
    fun openPicker(initialPaths: List<String>) {
        _state.update {
            it.copy(
                browseLevels = emptyList(),
                browseChecked = initialPaths,
                browseError = null,
                browseLoading = true,
            )
        }
        loadBrowseLevel(null, append = true)
    }

    /** 下一层；[path] 是服务端给的绝对路径（也就是将来填进表单的那个字符串）。 */
    fun drillPicker(path: String) {
        loadBrowseLevel(path, append = true)
    }

    /** 面包屑回跳：裁到该层为止。层级已经缓存在内存里，不必重打请求。 */
    fun pickerCrumbTo(index: Int) {
        _state.update {
            val levels = it.browseLevels.take(index + 1)
            it.copy(browseLevels = levels, browseError = null)
        }
    }

    fun togglePickerPath(path: String) {
        _state.update {
            val next = if (path in it.browseChecked) {
                it.browseChecked - path
            } else {
                it.browseChecked + path
            }
            it.copy(browseChecked = next)
        }
    }

    fun closePicker() {
        _state.update { it.copy(browseLevels = emptyList(), browseError = null, browseLoading = false) }
    }

    private fun loadBrowseLevel(path: String?, append: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(browseLoading = true, browseError = null) }
            try {
                val level = runner.call { api.browseLibraryDirs(path) }
                _state.update {
                    it.copy(
                        browseLevels = if (append) it.browseLevels + level else listOf(level),
                        browseLoading = false,
                        browseError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(browseLoading = false, browseError = e.message ?: "无法读取目录") }
            }
        }
    }

    // ---- 上传到库 ----

    fun openUpload(lib: LibraryDto) {
        _state.update {
            it.copy(
                upload = UploadUiState(
                    libraryId = lib.id,
                    libraryName = lib.name,
                    isChapter = lib.type == "chapter",
                ),
            )
        }
    }

    /** 追加待传文件；返回被扩展名过滤掉的文件数（交给弹层提示，与 Web 的 rejected 同职责）。 */
    fun addUploadFiles(files: List<PickedFile>) {
        _state.update { s ->
            val upload = s.upload ?: return@update s
            val seen = upload.files.mapTo(mutableSetOf()) { it.uri }
            s.copy(upload = upload.copy(files = upload.files + files.filter { it.uri !in seen }))
        }
    }

    /** 记录被过滤的文件（扩展名不支持）。 */
    fun noteRejected(names: List<String>) {
        if (names.isEmpty()) return
        _state.update { s ->
            val upload = s.upload ?: return@update s
            s.copy(upload = upload.copy(rejected = (upload.rejected + names).distinct()))
        }
    }

    fun removeUploadFile(uri: Uri) {
        _state.update { s ->
            val upload = s.upload ?: return@update s
            s.copy(upload = upload.copy(files = upload.files.filterNot { it.uri == uri }))
        }
    }

    fun startUpload() {
        val upload = _state.value.upload ?: return
        if (upload.uploading || upload.files.isEmpty()) return
        uploadJob?.cancel()
        _state.update { s ->
            s.upload?.let { s.copy(upload = it.copy(uploading = true, sentBytes = 0, error = null, result = null)) }
                ?: s
        }
        uploadJob = viewModelScope.launch {
            try {
                val res = uploader.upload(upload.libraryId, upload.files) { sent ->
                    _state.update { s ->
                        s.upload?.let { s.copy(upload = it.copy(sentBytes = sent)) } ?: s
                    }
                }
                _state.update { s ->
                    s.upload?.let { s.copy(upload = it.copy(uploading = false, result = res)) } ?: s
                }
                // 服务端写完就开扫：列表与库片的「收录数」都要跟上
                refresh()
                refreshScan()
                val skipped = res.skipped.size
                _messages.send(
                    UiMessage.ok(
                        if (upload.isChapter) {
                            "已解压 ${res.written} 个文件，正在扫描入库" +
                                (if (skipped > 0) "（跳过 $skipped 个不支持的文件）" else "")
                        } else {
                            "已写入 ${res.written} 个文件，正在扫描入库" +
                                (if (skipped > 0) "（跳过 $skipped 个不支持的文件）" else "")
                        },
                    ),
                )
            } catch (e: CancellationException) {
                // 用户点了「取消上传」：不算失败，回到文件选择态（Web 也是这个口径）
                _state.update { s ->
                    s.upload?.let { s.copy(upload = it.copy(uploading = false)) } ?: s
                }
            } catch (e: Exception) {
                _state.update { s ->
                    s.upload?.let { s.copy(upload = it.copy(uploading = false, error = e.message ?: "上传失败")) }
                        ?: s
                }
            }
        }
    }

    /** 取消正在进行的上传（留在面板里，可以重选文件）。 */
    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
    }

    fun closeUpload() {
        uploadJob?.cancel()
        uploadJob = null
        _state.update { it.copy(upload = null) }
    }

    override fun onCleared() {
        uploadJob?.cancel()
    }
}
