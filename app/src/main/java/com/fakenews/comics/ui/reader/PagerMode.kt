package com.fakenews.comics.ui.reader

import com.fakenews.comics.data.session.ReaderMode

/**
 * 阅读模式的「页」换算，与 Web 端 `utils/pagerMode.ts` 完全一致。
 *
 * `pageIndex` 一律指**模式页**：
 *   single / webtoon  1 图 = 1 页
 *   double            对开（见 [doubleSpreads]：首页/末页单占，中间两两成对）
 *   split             1 图 = 2 页（宽幅跨页图对半看；竖版页的第二页仍是同一张整图）
 * 阅读进度、路由、续读一律按**图索引**存，换模式不丢位置。
 *
 * 外壳（页码、滑块上限、越界判定）与各 Pager 必须共用这套换算，否则会出现
 * 「拆分模式拖不到后半页」「双页模式到章末翻不出下一章」这类页码域不一致的 bug。
 *
 * 双页的两个换算都要 [imageCount]：末页单占让配对偏移与总页数有关，
 * 光看图索引推不出它落在第几个对开（末尾那张尤其）。
 */

/**
 * 双页对开的排布：**首页与末页各自单独占一屏**，中间两两成对。
 *
 * 为什么不是简单的「相邻两图一跨」：绝大多数章节的图 0 是封面，硬配对会把封面和第一张
 * 内页挤在一屏，此后所有对开整体错位一页 —— 真正的跨页图被撕到两屏上，是双页模式
 * 最刺眼的错。首页单占之后，1/2、3/4… 才恢复成真正的对开。末页同理：最后一跨常常
 * 只剩一张，与其让它和空白并排缩成半屏，不如整屏给它。
 *
 * 没有做「横版页一律单张」那条：它需要每张图的宽高，而页元信息（PageMeta）
 * 只有 name/size，服务端列页时并不解码图片。要做要么每次列页全章解码（几百页的章节
 * 首屏会慢一个数量级），要么加一层尺寸缓存 —— 等真有人抱怨跨页图被拆再补。
 *
 * 结果按 imageCount 缓存：这个函数在页码、滑块、续读文案多处被调，不该每次重建列表。
 * 只在主线程调用（Composition / ViewModel），不做同步。
 */
private var cachedCount = -1
private var cachedSpreads: List<List<Int>> = emptyList()

fun doubleSpreads(imageCount: Int): List<List<Int>> {
    if (imageCount <= 0) return listOf(emptyList())
    if (imageCount == cachedCount) return cachedSpreads

    // 首页单占
    val out = ArrayList<List<Int>>(imageCount / 2 + 2)
    out.add(listOf(0))
    val last = imageCount - 1
    // 中间从图 1 起两两成对；配对必须止于末页之前，否则末页会被并进倒数第二跨
    var i = 1
    while (i < last) {
        out.add(if (i + 1 < last) listOf(i, i + 1) else listOf(i))
        i += 2
    }
    // 末页单占（只有一张图的章节上面已经处理完，这里不再重复压一页空跨）
    if (last > 0) out.add(listOf(last))

    cachedCount = imageCount
    cachedSpreads = out
    return out
}

fun modePageCount(mode: ReaderMode, imageCount: Int): Int = when (mode) {
    ReaderMode.DOUBLE -> maxOf(1, doubleSpreads(imageCount).size)
    ReaderMode.SPLIT -> imageCount * 2
    else -> imageCount
}

/** 图索引 → 模式页索引 */
fun imageToModePage(mode: ReaderMode, img: Int, imageCount: Int): Int = when (mode) {
    ReaderMode.DOUBLE -> {
        val spreads = doubleSpreads(imageCount)
        val hit = spreads.indexOfFirst { img in it }
        // 越界的图索引（换章瞬间 pages 还是旧的）统一落到最后一跨，别返回 -1 让调用方再夹一次
        if (hit >= 0) hit else spreads.lastIndex.coerceAtLeast(0)
    }
    ReaderMode.SPLIT -> img * 2
    else -> img
}

/** 模式页索引 → 图索引（不叠加对开内偏移） */
fun modePageToImage(mode: ReaderMode, page: Int, imageCount: Int): Int = when (mode) {
    ReaderMode.DOUBLE -> {
        val spreads = doubleSpreads(imageCount)
        spreads[page.coerceIn(0, spreads.lastIndex)].firstOrNull() ?: 0
    }
    ReaderMode.SPLIT -> page / 2
    else -> page
}

/**
 * 图索引在它所在对开内的偏移（0 或 1）。
 *
 * 不能再拿 `img % 2` 代替：首页单占之后，一跨里的第一张图索引可能是奇也可能是偶
 * （[1,2] 从奇数张起头），旧规则里「偶数开头」的假设不再成立。
 * 用偏移反推图索引的地方（进度、续读、切模式）必须走这里。
 */
fun doubleSubOffset(img: Int, imageCount: Int): Int {
    val first = modePageToImage(
        ReaderMode.DOUBLE,
        imageToModePage(ReaderMode.DOUBLE, img, imageCount),
        imageCount,
    )
    return (img - first).coerceAtLeast(0)
}

/**
 * 平移轨道的**动画起点槽位**：从 [from] 翻到相邻的 [to] 时，轨道先摆到哪个槽位再滑向中间。
 *
 * 起点必须是**旧页**所在的那一个槽位。摆成别的槽位（最容易写错的是取槽位的补数，
 * 即「新页从某一侧滑进来」那种直觉）会得到「再下一页居中、往回滑」的动画：方向反了，
 * 中点还会露出一帧根本没加载过的邻页。
 *
 * 与 Web 的 `from = slots.indexOf(prev)` 同源 —— 那里是先按新页码算出三个槽位、
 * 再回头找旧页落在哪。ltr 往后翻 → 旧页在左槽（0）、往前翻 → 右槽（2）；
 * rtl 的槽位是镜像的（左槽 = 下一页），于是两者对调。
 */
fun panEntrySlot(from: Int, to: Int, rtl: Boolean): Int = if ((to > from) == rtl) 2 else 0

/**
 * 当前读到的图索引（进度、预加载都以它为准）。
 *
 * 双页要叠上对开内的偏移 [doubleSub]（偏移本身由 [doubleSubOffset] 算出，不是图索引的
 * 奇偶）；末页是单张的对开会算出越界一页，clamp 掉。
 */
fun imageIndexOf(
    mode: ReaderMode,
    page: Int,
    doubleSub: Int,
    imageCount: Int,
): Int {
    if (imageCount <= 0) return 0
    val extra = if (mode == ReaderMode.DOUBLE) doubleSub else 0
    return (modePageToImage(mode, page, imageCount) + extra).coerceIn(0, imageCount - 1)
}
