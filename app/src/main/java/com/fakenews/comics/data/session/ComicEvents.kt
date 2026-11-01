package com.fakenews.comics.data.session

import com.fakenews.comics.data.model.ComicDto
import com.fakenews.comics.data.model.HistoryItemDto
import com.fakenews.comics.data.model.ProgressDto
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 跨页面的数据变更通知（收藏、进度重置、删除阅读记录）。
 *
 * 书架/收藏/记录三个列表各自持有服务端快照，详情页改完收藏再按返回键时，
 * 列表没有理由知道数据变了。与其每次返回都整页重取（会把无限滚动退回第一页），
 * 不如让改数据的一方广播一条事件，列表就地打补丁。
 */
@Singleton
class ComicEvents @Inject constructor() {
    sealed interface Event {
        data class FavoriteChanged(val comicId: String, val favorite: Boolean) : Event

        /**
         * 漫画库变了（新建 / 改路径 / 删除）。
         *
         * 这一类不以某部漫画为主体：删库会级联清掉库内所有条目，那些条目若不重取就会
         * 一直停在首页/书架的快照里（删除本身不触发扫描，没有别的时机纠正它）。
         * 列表只能整页重取 —— 没有 comicId 可打补丁，而状元数据本来就该由服务端重新给。
         */
        data object LibraryChanged : Event

        /** 进度被清除：列表卡片进度条、历史条目都要跟着消失。 */
        data class ProgressCleared(val comicId: String) : Event

        /**
         * 阅读器上报了新进度。percent 用服务端回写的全书值 —— 章内百分比会
         * 把「读完第 1 章的多章书」显示成 100%。
         */
        data class ProgressChanged(
            val comicId: String,
            val percent: Double,
            val chapterId: String,
            val chapterIndex: Int,
            val pageIndex: Int,
            val pageCount: Int,
        ) : Event

        /**
         * 元数据被改写（详情页编辑信息 / 单本重扫）。
         *
         * 列表各自持有快照，重拉整页会把无限滚动退回第一页，所以照其它事件的路子就地打补丁。
         * 字段都可空：改标题不必把标签也算进去，服务端只认传了的那几项。
         */
        data class MetaChanged(
            val comicId: String,
            val title: String? = null,
            val author: String? = null,
        ) : Event
    }

    private val _events = MutableSharedFlow<Event>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    fun emit(event: Event) {
        _events.tryEmit(event)
    }
}

/** 列表侧就地打补丁：percent 用服务端回写的全书值，别拿章内值。 */
fun progressOf(current: ProgressDto?, event: ComicEvents.Event.ProgressChanged): ProgressDto =
    (current ?: ProgressDto(comicId = event.comicId)).copy(
        comicId = event.comicId,
        chapterId = event.chapterId,
        chapterIndex = event.chapterIndex,
        pageIndex = event.pageIndex,
        pageCount = event.pageCount,
        percent = event.percent,
        updatedAt = System.currentTimeMillis(),
    )

/** 服务端按全书百分比判定「读完」，客户端跟着同一口径走。 */
fun readStatusOf(percent: Double): String = if (percent >= 100.0) "finished" else "reading"

/** 书架/收藏卡片按进度事件就地更新。 */
fun ComicDto.withProgress(event: ComicEvents.Event.ProgressChanged): ComicDto = copy(
    progress = progressOf(progress, event),
    readStatus = readStatusOf(event.percent),
    lastReadAt = System.currentTimeMillis(),
)

/**
 * 详情页编辑信息后就地改标题/作者。
 *
 * 只改传了的那几项：服务端用指针区分「没传」与「传了空字符串」，
 * 这里 null 一律理解为「这一项没动」，不能顺手写空。
 */
fun ComicDto.withMeta(event: ComicEvents.Event.MetaChanged): ComicDto = copy(
    title = event.title ?: title,
    author = event.author ?: author,
)

/** 阅读记录的标题同样要跟着更新（记录行显示的是它自己的快照） */
fun HistoryItemDto.withMeta(event: ComicEvents.Event.MetaChanged): HistoryItemDto = copy(
    title = event.title ?: title,
    author = event.author ?: author,
)
