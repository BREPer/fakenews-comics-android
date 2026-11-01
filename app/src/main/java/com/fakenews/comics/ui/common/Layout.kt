package com.fakenews.comics.ui.common

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 宽屏（平板 / 展开态折叠屏）的版式常量（2026-10-29 加）。
 *
 * 之前 Android 所有页面都按手机写死：封面网格 `Fixed(3)`、内容一律铺满。
 * 放到平板上就成了「3 个被拉到 250dp 宽的封面」和「一行文字横跨 1000dp」——
 * 手机上的密度与阅读节奏整个没了。Web 那边靠断点（`sm/md/lg/xl`）处理，
 * Android 没有 CSS 断点，这里补一份同口径的。
 */

/**
 * 主内容在宽屏上的最大宽度。
 *
 * 1080 是「一行文字不至于长到要来回找下一行」的经验上限，也接近 Web 的
 * `max-w-7xl`(1280px) 减去侧栏后的实际内容宽。**只限制内容，不限制背景**：
 * 页面底色（AmbientBackdrop）与底栏仍然铺满，两侧留白只是内容不跟着长。
 */
val ContentMaxWidth = 1080.dp

/**
 * 网格左右内距（Web `px-4`）。列数按**扣掉它之后**的宽度算，
 * 否则平板边缘那两列会比中间窄一截。
 */
val GridSidePadding = 16.dp

/**
 * 封面网格列数。
 *
 * 入参是**布局期的真实可用宽度**（`BoxWithConstraints` 的 `maxWidth`），
 * 不是 `LocalConfiguration.screenWidthDp`：本项目的 Activity 声明了
 * `configChanges="orientation|screenSize|density"`，改成**分屏 / 折叠屏展开 /
 * 改分辨率**时 Activity 不重建，Configuration 那条链更新得比布局晚 ——
 * 实测在模拟器上把画布改成 1600×2560@320 后，网格仍按旧宽度排出 3 个 250dp 的
 * 巨型封面，且右侧溢出屏外。布局期宽度永远和当前这一帧对得上。
 *
 * 目标是让卡片落在 100~140dp 这个区间（手机上 3 列约 104dp，与现在一致）：
 *   328 → 3 列 / 101dp    768 → 6 列 / 112dp
 *   480 → 4 列 / 111dp   1024 → 7 列 / 131dp
 *   600 → 5 列 / 106dp   1080 → 8 列 / 120dp
 */
fun comicGridColumns(availableWidth: Dp): GridCells =
    GridCells.Fixed(columnsFor((availableWidth - GridSidePadding * 2).value.roundToInt()))

private fun columnsFor(widthDp: Int): Int = when {
    widthDp < 400 -> 3
    widthDp < 560 -> 4
    widthDp < 720 -> 5
    widthDp < 900 -> 6
    widthDp < 1080 -> 7
    else -> 8
}
