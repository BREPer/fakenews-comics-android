package com.fakenews.comics.ui.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.model.ComicDetailDto
import com.fakenews.comics.data.model.FavoriteBody
import com.fakenews.comics.data.model.UpdateComicBody
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.net.ImageUrls
import com.fakenews.comics.data.session.ChapterView
import com.fakenews.comics.data.session.ComicEvents
import com.fakenews.comics.data.session.ViewPrefs
import com.fakenews.comics.data.session.progressOf
import com.fakenews.comics.data.session.readStatusOf
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
data class DetailUiState(
    val loading: Boolean = true,
    val detail: ComicDetailDto? = null,
    val error: String? = null,
    val favoritePending: Boolean = false,
    val resetting: Boolean = false,
    /** 重置失败的原因：确认框保持打开并把它显示出来（Web ConfirmDialog 的 error 槽） */
    val resetError: String? = null,
    /** 编辑信息保存中 */
    val saving: Boolean = false,
    /** 单本重扫中 */
    val rescanning: Boolean = false,
    /** 章节区排版：列表 / 四格（本机记忆，见 ViewPrefs） */
    val chapterView: ChapterView = ChapterView.LIST,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val api: FakenewsApi,
    private val runner: ApiRunner,
    private val imageUrls: ImageUrls,
    private val events: ComicEvents,
    private val viewPrefs: ViewPrefs,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState(chapterView = viewPrefs.detailChapterView))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** 信息保存成功：界面据此收起编辑面板（失败时不发，面板留在原地让人改） */
    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private var comicId: String? = null

    init {
        // 阅读器上报进度时详情页还在返回栈里：就地改快照，回来才不会看到旧页码
        viewModelScope.launch {
            events.events.collect { event ->
                val changed = event as? ComicEvents.Event.ProgressChanged ?: return@collect
                if (changed.comicId != comicId) return@collect
                _state.update { s ->
                    val detail = s.detail ?: return@update s
                    s.copy(
                        detail = detail.copy(
                            progress = progressOf(detail.progress, changed),
                            readStatus = readStatusOf(changed.percent),
                            lastReadAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    fun load(id: String) {
        if (comicId == id && (_state.value.detail != null || _state.value.loading)) return
        comicId = id
        _state.value = DetailUiState(loading = true)
        fetch(id)
    }

    fun retry() {
        val id = comicId ?: return
        _state.update { it.copy(loading = true, error = null) }
        fetch(id)
    }

    fun coverUrl(width: Int = 480): String? = comicId?.let { imageUrls.cover(it, width) }

    /** 章节区排版切换：先落盘再改状态，下次进详情页仍是这个视图 */
    fun setChapterView(view: ChapterView) {
        viewPrefs.detailChapterView = view
        _state.update { it.copy(chapterView = view) }
    }

    fun toggleFavorite() {
        val detail = _state.value.detail ?: return
        if (_state.value.favoritePending) return
        _state.update { it.copy(favoritePending = true) }
        viewModelScope.launch {
            try {
                if (detail.isFavorite) {
                    runner.call { api.removeFavorite(detail.id) }
                } else {
                    runner.call { api.addFavorite(FavoriteBody(detail.id)) }
                }
                val next = !detail.isFavorite
                _state.update { it.copy(favoritePending = false, detail = it.detail?.copy(isFavorite = next)) }
                events.emit(ComicEvents.Event.FavoriteChanged(detail.id, next))
                _messages.send(UiMessage.ok(if (next) "已加入收藏" else "已取消收藏"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(favoritePending = false) }
                _messages.send(UiMessage.err("收藏操作失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    fun resetProgress() {
        val detail = _state.value.detail ?: return
        if (_state.value.resetting) return
        _state.update { it.copy(resetting = true, resetError = null) }
        viewModelScope.launch {
            try {
                runner.call { api.resetProgress(detail.id) }
                _state.update {
                    it.copy(
                        resetting = false,
                        resetError = null,
                        detail = it.detail?.copy(progress = null, readStatus = "unread", lastReadAt = null),
                    )
                }
                events.emit(ComicEvents.Event.ProgressCleared(detail.id))
                _messages.send(UiMessage.ok("阅读进度已重置"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 失败时不清 resetError：确认框还开着，原因就写在框里（只发 toast 的话，
                // 框已经被关掉，用户只看到「点了没反应 + 一条一闪而过的提示」）
                _state.update {
                    it.copy(resetting = false, resetError = e.message ?: "重置失败，请重试")
                }
            }
        }
    }

    /**
     * 编辑信息（title/author/description/tags）。
     *
     * 与 Web 端 `Detail.tsx` 同一口径：只把**填过的**字段发出去——服务端用指针区分
     * 「没传」与「传了空字符串」，多传一个空字符串就会把已有内容清掉。
     * 保存后服务端会把这条记录置为 meta_locked，重扫不会再覆盖用户改过的东西。
     */
    fun saveMeta(title: String, author: String, description: String, tags: List<String>) {
        val detail = _state.value.detail ?: return
        if (_state.value.saving) return
        val id = detail.id
        val trimTitle = title.trim()
        val trimAuthor = author.trim()
        val trimDesc = description.trim()
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                runner.call {
                    api.updateComic(
                        id,
                        UpdateComicBody(
                            title = trimTitle.ifEmpty { null },
                            author = trimAuthor.ifEmpty { null },
                            description = trimDesc.ifEmpty { null },
                            // 标签总是整份提交（服务端按「去重去空后的列表」覆盖），空列表就是清空
                            tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                        ),
                    )
                }
                _state.update {
                    it.copy(
                        saving = false,
                        detail = it.detail?.copy(
                            title = trimTitle.ifEmpty { it.detail.title },
                            author = trimAuthor.ifEmpty { it.detail.author },
                            description = trimDesc.ifEmpty { it.detail.description },
                            tags = tags,
                        ),
                    )
                }
                events.emit(
                    ComicEvents.Event.MetaChanged(
                        comicId = id,
                        title = trimTitle.ifEmpty { null },
                        author = trimAuthor.ifEmpty { null },
                    ),
                )
                _messages.send(UiMessage.ok("信息已保存"))
                _saved.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(UiMessage.err("保存失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    /**
     * 单部重扫：替换文件（比如补了一卷）后刷新这一本的章节/页数。
     *
     * 接口是同步返回的，成功后必须**重拉详情**：Web 端早期版本只清列表 key，
     * 界面上「重扫」等于没反应，就是这个坑。
     */
    fun rescan() {
        val detail = _state.value.detail ?: return
        if (_state.value.rescanning) return
        val id = detail.id
        _state.update { it.copy(rescanning = true) }
        viewModelScope.launch {
            try {
                val res = runner.call { api.rescanComic(id) }
                _state.update { it.copy(rescanning = false) }
                _messages.send(UiMessage.ok("重扫完成：${res.chapterCount} 章 · ${res.pageCount} 页"))
                // 章节数/页数可能变了，客户端这份快照已经过期
                fetch(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(rescanning = false) }
                _messages.send(UiMessage.err("重扫失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    private fun fetch(id: String) {
        viewModelScope.launch {
            try {
                val detail = runner.call { api.comicDetail(id) }
                _state.update { it.copy(loading = false, detail = detail, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }
}
