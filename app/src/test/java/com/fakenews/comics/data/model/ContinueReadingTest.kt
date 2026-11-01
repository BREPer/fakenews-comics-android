package com.fakenews.comics.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueReadingTest {
    private fun item(
        id: String,
        percent: Double?,
        lastReadAt: Long = 0L,
    ): HistoryItemDto = HistoryItemDto(
        id = id,
        title = "漫画 $id",
        progress = percent?.let { ProgressDto(comicId = id, percent = it) },
        lastReadAt = lastReadAt,
    )

    @Test
    fun `读完的不进横条`() {
        val items = listOf(item("a", 100.0), item("b", 99.9), item("c", 0.0))
        assertEquals(listOf("b", "c"), continueReadingOf(items).map { it.id })
    }

    @Test
    fun `没有进度的不进横条`() {
        val items = listOf(item("a", null), item("b", 12.0))
        assertEquals(listOf("b"), continueReadingOf(items).map { it.id })
    }

    @Test
    fun `只取前 12 条且保持服务端顺序`() {
        val items = (0 until 20).map { item("c$it", 10.0, lastReadAt = 20L - it) }
        val result = continueReadingOf(items)
        assertEquals(CONTINUE_LIMIT, result.size)
        assertEquals((0 until 12).map { "c$it" }, result.map { it.id })
    }

    @Test
    fun `整本读完后横条为空而不是报错`() {
        val items = (0 until 5).map { item("c$it", 100.0) }
        assertTrue(continueReadingOf(items).isEmpty())
    }

    @Test
    fun `拉取窗口要大于展示条数`() {
        // 只取 12 条的话，刚读完 12 本短篇后横条会整块消失，而更早还在读的书反而没有入口
        assertTrue(CONTINUE_FETCH_SIZE > CONTINUE_LIMIT)
    }
}
