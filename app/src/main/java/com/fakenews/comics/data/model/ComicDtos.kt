package com.fakenews.comics.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
@Immutable
data class ProgressDto(
    val comicId: String = "",
    val chapterId: String = "",
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val percent: Double = 0.0,
    val updatedAt: Long = 0,
)

/*
 * 关于 `@Immutable`：
 *
 * 这些 DTO 都有 `List<String>` 之类的字段，Compose 编译器**推断不出**它们的稳定性
 * （`List` 是接口，可能是可变实现），于是整个类被判成 unstable。后果不是「传进去会变」，
 * 而是连锁的：
 *   1. 卡片/列表行这类 Composable 的参数被判 unstable，父级每次重组它都要跟着重跑；
 *   2. 捕获了它的 lambda（`onClick = { onOpenComic(comic.id) }`）**无法被 memoize**，
 *      每次重组都是新实例 —— 于是「卡片没变、只是父级重组了」也会让整张卡重画。
 * 一屏 12 张卡、无限滚动每次追加 36 条，这个乘数很实在。
 *
 * 标注 `@Immutable` 是开发者对编译器的承诺：这些实例创建后不再变化。由 kotlinx.serialization
 * 反序列化出来的 data class 天然满足（全是 val，唯一的 List 字段由构造时捕获且从不修改），
 * 不满足的是「有人往 tags 里 add」—— 本项目没有这种写法。
 *
 * 验证方式：`./gradlew :app:compileDebugKotlin` 会在 `build/compose-reports/` 下产出
 * `…-classes.txt`，里面应显示 `stable class ComicDto`。改这里之后记得看一眼那份报告，
 * 别让稳定性悄悄退化。
 */
@Serializable
@Immutable
data class ComicDto(
    val id: String,
    val libraryId: String = "",
    val title: String = "",
    val rawTitle: String = "",
    val author: String? = null,
    val description: String? = null,
    val format: String = "",
    val sizeBytes: Long = 0,
    val chapterCount: Int = 0,
    val pageCount: Int = 0,
    val tags: List<String> = emptyList(),
    val fileModifiedAt: Long = 0,
    val addedAt: Long = 0,
    val progress: ProgressDto? = null,
    val isFavorite: Boolean = false,
    val lastReadAt: Long? = null,
    val readStatus: String = "unread",
)

@Serializable
@Immutable
data class ChapterDto(
    val id: String,
    val index: Int = 0,
    val title: String = "",
    val pageCount: Int = 0,
    val sizeBytes: Long = 0,
)

/** 章节页元信息（GET /api/comics/{id}/chapters/{ch}/pages）。 */
@Serializable
@Immutable
data class PageMetaDto(
    val index: Int = 0,
    val name: String = "",
    val size: Long = 0,
)

@Serializable
data class PagesDto(
    val pages: List<PageMetaDto> = emptyList(),
)

@Serializable
@Immutable
data class ComicDetailDto(
    val id: String,
    val libraryId: String = "",
    val title: String = "",
    val rawTitle: String = "",
    val author: String? = null,
    val description: String? = null,
    val format: String = "",
    val sizeBytes: Long = 0,
    val chapterCount: Int = 0,
    val pageCount: Int = 0,
    val tags: List<String> = emptyList(),
    val fileModifiedAt: Long = 0,
    val addedAt: Long = 0,
    val progress: ProgressDto? = null,
    val isFavorite: Boolean = false,
    val lastReadAt: Long? = null,
    val readStatus: String = "unread",
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class PagedDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 0,
    val hasMore: Boolean = false,
)

@Serializable
data class ItemsDto<T>(
    val items: List<T> = emptyList(),
)

@Serializable
@Immutable
data class FacetDto(
    val value: String = "",
    val count: Int = 0,
)

@Serializable
@Immutable
data class FacetsDto(
    val formats: List<FacetDto> = emptyList(),
    val tags: List<FacetDto> = emptyList(),
)

@Serializable
@Immutable
data class HistoryItemDto(
    val id: String,
    val libraryId: String = "",
    val title: String = "",
    val rawTitle: String = "",
    val author: String? = null,
    val description: String? = null,
    val format: String = "",
    val sizeBytes: Long = 0,
    val chapterCount: Int = 0,
    val pageCount: Int = 0,
    val tags: List<String> = emptyList(),
    val fileModifiedAt: Long = 0,
    val addedAt: Long = 0,
    val progress: ProgressDto? = null,
    val isFavorite: Boolean = false,
    val lastReadAt: Long? = null,
    val readStatus: String = "unread",
    val chapterId: String = "",
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val progressPageCount: Int = 0,
)

@Serializable
data class HistoryPageDto(
    val items: List<HistoryItemDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class FavoriteBody(
    val comicId: String,
)

@Serializable
data class PutProgressBody(
    val comicId: String,
    val chapterId: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val percent: Double,
    /** 只有「撤销删除阅读记录」才传：按原时间戳写回，记录留在原来的位置。 */
    val updatedAt: Long? = null,
)

@Serializable
data class PutProgressResult(
    val ok: Boolean = false,
    val percent: Double = 0.0,
)

val ComicDto.isFinished: Boolean get() = readStatus == "finished"

/**
 * 卡片副标题：单页书显示页数，其余显示章数；有进度时追加百分比（与 Web 版同规则）。
 *
 * 百分比必须 **四舍五入**，不能 `toInt()` 截断 —— Web 是 `Math.round`，
 * 截断会让 49.6% 显示成 49%、99.4% 显示成 99%（看着像「读完了却还差 1%」）。
 */
fun ComicDto.subtitle(): String {
    val base = if (chapterCount > 1 || pageCount == 0) {
        if (chapterCount > 0) "$chapterCount 章" else ""
    } else {
        "$pageCount 页"
    }
    val pct = progress
    if (pct == null || pct.percent <= 0 || isFinished) return base
    val label = "${pct.percent.roundToInt()}%"
    return if (base.isEmpty()) label else "$base · $label"
}
