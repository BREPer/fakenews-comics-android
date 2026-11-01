package com.fakenews.comics.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 卡片副标题的规则（对齐 Web `components/comic/ComicCard.tsx:99-110`）：
 *
 * ```
 * comic.chapterCount > 1 || comic.pageCount === 0
 *   ? (comic.chapterCount > 0 ? `${chapterCount} 章` : '')
 *   : `${pageCount} 页`
 * ```
 * 再在 `progress.percent > 0 && readStatus !== 'finished'` 时追加 ` · ${Math.round(percent)}%`。
 *
 * 三处容易写错、且都真实踩过的地方：
 *   1. **百分比必须四舍五入**（Web 是 `Math.round`）。早先 Kotlin 侧写的 `toInt()` 是截断，
 *      49.6% 会显示成 49%、99.6% 显示成 99%（看着像「明明读完了却还差 1%」）；
 *   2. 章数 > 1 时显示章数而不是页数（扫描期 pageCount 只统计「散图页」，
 *      章全是压缩包/PDF 时会是个误导性的 0 或很小的数）；
 *   3. 读完（`readStatus == "finished"`）时不再追加百分比 —— 卡片上已经有「已读完」角标。
 */
class ComicSubtitleTest {

    private fun comic(
        chapterCount: Int = 1,
        pageCount: Int = 20,
        readStatus: String = "unread",
        percent: Double? = null,
    ) = ComicDto(
        id = "c1",
        title = "t",
        chapterCount = chapterCount,
        pageCount = pageCount,
        readStatus = readStatus,
        progress = percent?.let {
            ProgressDto(comicId = "c1", percent = it)
        },
    )

    @Test
    fun singleChapterShowsPageCount() {
        assertEquals("20 页", comic(pageCount = 20).subtitle())
    }

    @Test
    fun multiChapterShowsChapterCount() {
        assertEquals("12 章", comic(chapterCount = 12, pageCount = 480).subtitle())
    }

    @Test
    fun zeroPagesFallsBackToChapterCount() {
        // 章全是 PDF/压缩包时 pageCount 是 0，显示「0 页」会像坏掉了
        assertEquals("3 章", comic(chapterCount = 3, pageCount = 0).subtitle())
    }

    @Test
    fun noChaptersAndNoPagesShowsEmpty() {
        assertEquals("", comic(chapterCount = 0, pageCount = 0).subtitle())
    }

    @Test
    fun percentIsRoundedNotTruncated() {
        // 49.6 → 50（截断会得到 49）
        assertEquals("20 页 · 50%", comic(pageCount = 20, percent = 49.6).subtitle())
        // 99.6 → 100（截断会得到 99，看起来永远读不完）
        assertEquals("20 页 · 100%", comic(pageCount = 20, percent = 99.6).subtitle())
        // 0.4 → 0（虽然 >0 所以会追加上去，四舍五入后是 0%）
        assertEquals("20 页 · 0%", comic(pageCount = 20, percent = 0.4).subtitle())
    }

    @Test
    fun zeroPercentIsNotAppended() {
        assertEquals("12 章", comic(chapterCount = 12, percent = 0.0).subtitle())
    }

    @Test
    fun finishedComicDoesNotAppendPercent() {
        // 卡片上已有「已读完」角标，再来一个「· 100%」是重复噪音
        assertEquals("20 页", comic(pageCount = 20, readStatus = "finished", percent = 100.0).subtitle())
    }
}
