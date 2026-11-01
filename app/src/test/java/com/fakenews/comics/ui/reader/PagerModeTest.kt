package com.fakenews.comics.ui.reader

import com.fakenews.comics.data.session.ReaderMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 页码域换算的单测。
 *
 * 这是阅读器最容易出隐蔽 bug 的一层：`double` 是**对开**（首页/末页单占，中间两两成对）、
 * `split` 1 图 2 页，外壳（页码/滑块上限/越界判定）与各 Pager 必须共用同一套换算，
 * 差一个除数就会出现「拆分模式拖不到后半页」「双页模式到章末翻不出下一章」。
 * 进度与续读一律走**图索引**，所以四种模式之间来回切换不能丢位置——这也是这里的重点。
 */
class PagerModeTest {

    /* ---------------- 页数 ---------------- */

    @Test
    fun `单页与卷轴一图一页`() {
        assertEquals(0, modePageCount(ReaderMode.SINGLE, 0))
        assertEquals(12, modePageCount(ReaderMode.SINGLE, 12))
        assertEquals(12, modePageCount(ReaderMode.WEBTOON, 12))
    }

    @Test
    fun `双页首页与末页各占一屏`() {
        // 1 张：只有首页
        assertEquals(1, modePageCount(ReaderMode.DOUBLE, 1))
        // 4 张 = [0] + [1,2] + [3]
        assertEquals(3, modePageCount(ReaderMode.DOUBLE, 4))
        // 5 张 = [0] + [1,2] + [3] + [4]（中间那张单着，末页仍单占）
        assertEquals(4, modePageCount(ReaderMode.DOUBLE, 5))
        // 6 张 = [0] + [1,2] + [3,4] + [5]
        assertEquals(4, modePageCount(ReaderMode.DOUBLE, 6))
        // 8 张 = [0] + [1,2] + [3,4] + [5,6] + [7]
        assertEquals(5, modePageCount(ReaderMode.DOUBLE, 8))
    }

    @Test
    fun `双页的对开分组`() {
        assertEquals(listOf(listOf(0)), doubleSpreads(1))
        assertEquals(listOf(listOf(0), listOf(1)), doubleSpreads(2))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), doubleSpreads(4))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)), doubleSpreads(6))
    }

    @Test
    fun `双页空章节仍有一页可显示`() {
        // 0 张图不能算出 0 页，否则滑块 valueRange 0f..-1f 直接崩
        assertEquals(1, modePageCount(ReaderMode.DOUBLE, 0))
        assertEquals(listOf(emptyList<Int>()), doubleSpreads(0))
    }

    @Test
    fun `拆分一张图算两页`() {
        assertEquals(0, modePageCount(ReaderMode.SPLIT, 0))
        assertEquals(2, modePageCount(ReaderMode.SPLIT, 1))
        assertEquals(24, modePageCount(ReaderMode.SPLIT, 12))
    }

    /* ---------------- 图索引 <-> 模式页 ---------------- */

    @Test
    fun `单页与卷轴两台换算互为逆`() {
        for (img in 0 until 20) {
            assertEquals(img, imageToModePage(ReaderMode.SINGLE, img, 20))
            assertEquals(img, modePageToImage(ReaderMode.SINGLE, img, 20))
            assertEquals(img, imageToModePage(ReaderMode.WEBTOON, img, 20))
            assertEquals(img, modePageToImage(ReaderMode.WEBTOON, img, 20))
        }
    }

    @Test
    fun `双页图索引落到对开序号`() {
        val n = 12 // [0] [1,2] [3,4] [5,6] [7,8] [9,10] [11]
        assertEquals(0, imageToModePage(ReaderMode.DOUBLE, 0, n))
        assertEquals(1, imageToModePage(ReaderMode.DOUBLE, 1, n))
        assertEquals(1, imageToModePage(ReaderMode.DOUBLE, 2, n))
        assertEquals(3, imageToModePage(ReaderMode.DOUBLE, 5, n))
        assertEquals(6, imageToModePage(ReaderMode.DOUBLE, 11, n))
    }

    @Test
    fun `双页模式页算回对开的第一张`() {
        val n = 12
        assertEquals(0, modePageToImage(ReaderMode.DOUBLE, 0, n))
        assertEquals(1, modePageToImage(ReaderMode.DOUBLE, 1, n))
        assertEquals(3, modePageToImage(ReaderMode.DOUBLE, 2, n))
        assertEquals(11, modePageToImage(ReaderMode.DOUBLE, 6, n))
    }

    @Test
    fun `拆分左半右半`() {
        // 图 1 摊成第 2、3 页
        assertEquals(2, imageToModePage(ReaderMode.SPLIT, 1, 8))
        assertEquals(0, imageToModePage(ReaderMode.SPLIT, 0, 8))
        // 第 4、5 页同属图 2
        assertEquals(2, modePageToImage(ReaderMode.SPLIT, 4, 8))
        assertEquals(2, modePageToImage(ReaderMode.SPLIT, 5, 8))
    }

    /* ---------------- 当前图索引（含对开偏移） ---------------- */

    @Test
    fun `图索引按对开偏移叠加`() {
        // 第 1 跨（[0]）单张，偏移只可能是 0
        assertEquals(0, imageIndexOf(ReaderMode.DOUBLE, 0, doubleSub = 0, imageCount = 10))
        // 第 2 跨（[1,2]）的左页 = 图 1
        assertEquals(1, imageIndexOf(ReaderMode.DOUBLE, 1, doubleSub = 0, imageCount = 10))
        // 第 2 跨的右页 = 图 2
        assertEquals(2, imageIndexOf(ReaderMode.DOUBLE, 1, doubleSub = 1, imageCount = 10))
        // 第 3 跨（[3,4]）的右页 = 图 4
        assertEquals(4, imageIndexOf(ReaderMode.DOUBLE, 2, doubleSub = 1, imageCount = 10))
    }

    @Test
    fun `非双页模式忽略偏移`() {
        assertEquals(3, imageIndexOf(ReaderMode.SINGLE, 3, doubleSub = 1, imageCount = 10))
        assertEquals(1, imageIndexOf(ReaderMode.SPLIT, 2, doubleSub = 1, imageCount = 10))
    }

    @Test
    fun `末页单张的偏移不越界`() {
        // 5 张图 = [0] [1,2] [3] [4]：第 4 跨只有图 4，偏移 1 会算出 5 —— 必须夹回来
        assertEquals(4, imageIndexOf(ReaderMode.DOUBLE, 3, doubleSub = 1, imageCount = 5))
    }

    @Test
    fun `没有图时读到的图索引是 0`() {
        assertEquals(0, imageIndexOf(ReaderMode.DOUBLE, 3, doubleSub = 1, imageCount = 0))
        assertEquals(0, imageIndexOf(ReaderMode.SINGLE, 3, doubleSub = 0, imageCount = 0))
    }

    @Test
    fun `对开内偏移不能再用图索引奇偶`() {
        val n = 8 // [0] [1,2] [3,4] [5,6] [7]
        // 图 1 在 [1,2] 里是第 0 张（奇数却被当作左页），img % 2 会算成 1
        assertEquals(0, doubleSubOffset(1, n))
        assertEquals(1, doubleSubOffset(2, n))
        // 单张跨恒为 0
        assertEquals(0, doubleSubOffset(0, n))
        assertEquals(0, doubleSubOffset(7, n))
    }

    /* ---------------- 换模式不丢位置 ---------------- */

    @Test
    fun `任意图索引来回切四种模式都落在同一张图`() {
        val imageCount = 12
        for (img in 0 until imageCount) {
            for (target in ReaderMode.entries) {
                // 先落到目标模式，再算回图索引：双页要带上「我在这一跨的第几张」
                val page = imageToModePage(target, img, imageCount)
                val sub = if (target == ReaderMode.DOUBLE) doubleSubOffset(img, imageCount) else 0
                assertEquals("图 $img 切到 $target 后位置丢了", img, imageIndexOf(target, page, sub, imageCount))
            }
        }
    }

    @Test
    fun `双页切回单页保留跨内偏移`() {
        val imageCount = 10
        // 双页第 2 跨（[1,2]）的右页（图 2）
        val image = imageIndexOf(ReaderMode.DOUBLE, 1, doubleSub = 1, imageCount = imageCount)
        assertEquals(2, image)
        // 切回单页：应落在图 2，而不是跨首的图 1
        assertEquals(2, imageToModePage(ReaderMode.SINGLE, image, imageCount))
    }

    /* ---------------- 平移轨道的动画起点槽位 ---------------- */

    @Test
    fun `起点槽位就是旧页在新槽位里的位置`() {
        // 与 Web 的 slots.indexOf(prev) 同一套算法：先按新页码铺出三个槽位，再找旧页落在哪。
        // 平移轨道靠这个位置起步，取错（典型是取补数）整段动画就会反向。
        fun slotOf(to: Int, from: Int, rtl: Boolean): Int {
            val slots = if (rtl) listOf(to + 1, to, to - 1) else listOf(to - 1, to, to + 1)
            return slots.indexOf(from)
        }
        for (rtl in listOf(false, true)) {
            for (from in 1 until 9) {
                for (delta in listOf(-1, 1)) {
                    val to = from + delta
                    assertEquals(
                        "from=$from to=$to rtl=$rtl",
                        slotOf(to, from, rtl),
                        panEntrySlot(from, to, rtl),
                    )
                }
            }
        }
    }

    @Test
    fun `ltr 往后翻从左槽起步、往前翻从右槽起步`() {
        // 往后翻：旧页在左，于是画面整体向左流，新页从右边进来
        assertEquals(0, panEntrySlot(from = 3, to = 4, rtl = false))
        // 往前翻：旧页在右，画面整体向右流
        assertEquals(2, panEntrySlot(from = 3, to = 2, rtl = false))
    }

    @Test
    fun `rtl 的槽位镜像后两者对调`() {
        // 日漫右翻：下一页在左边，往后翻时旧页落在右槽，画面整体向右流
        assertEquals(2, panEntrySlot(from = 3, to = 4, rtl = true))
        assertEquals(0, panEntrySlot(from = 3, to = 2, rtl = true))
    }

    @Test
    fun `起点槽位只会是左右两端，不会是中间`() {
        for (rtl in listOf(false, true)) {
            for (from in 0 until 10) {
                for (delta in listOf(-1, 1)) {
                    val slot = panEntrySlot(from, from + delta, rtl)
                    assert(slot == 0 || slot == 2) { "起点落在中间槽：$slot" }
                }
            }
        }
    }

    @Test
    fun `拆分模式的后半页不会掉队`() {
        val imageCount = 8
        // 拆分第 5 页 = 图 2 的右半
        assertEquals(2, modePageToImage(ReaderMode.SPLIT, 5, imageCount))
        // 同一张图的两半映射回同一个图索引，进度不会在左右半之间反复横跳
        assertEquals(
            modePageToImage(ReaderMode.SPLIT, 4, imageCount),
            modePageToImage(ReaderMode.SPLIT, 5, imageCount),
        )
        // 8 张图 = 16 页（每张对半成两页）
        assertEquals(16, modePageCount(ReaderMode.SPLIT, imageCount))
    }
}
