package com.fakenews.comics.data.session

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 章节视图偏好的解析与 Web 契约一致：
 * localStorage 里存的是 `'list' | 'grid'` 两个字符串，读不到/读到脏值一律回落到列表。
 */
class ChapterViewTest {

    @Test
    fun parsesGrid() {
        assertEquals(ChapterView.GRID, ChapterView.from("grid"))
    }

    @Test
    fun parsesList() {
        assertEquals(ChapterView.LIST, ChapterView.from("list"))
    }

    @Test
    fun missingValueFallsBackToList() {
        assertEquals(ChapterView.LIST, ChapterView.from(null))
    }

    @Test
    fun unknownValueFallsBackToList() {
        assertEquals(ChapterView.LIST, ChapterView.from("table"))
    }

    /** 落盘用的字面量必须保持 Web 那两个值，否则老用户升级后记忆会丢 */
    @Test
    fun keysMatchWebLiterals() {
        assertEquals("list", ChapterView.LIST.key)
        assertEquals("grid", ChapterView.GRID.key)
    }
}
