package com.fakenews.comics.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 自绘字形集：全部照 lucide 造型用 Canvas 画。
 *
 * 起因：工程只引入 `material-icons-core`（整包 `material-icons-extended` 会多出 ~4MB），
 * 而核心集里没有 LayoutGrid / ListOrdered / History / ImageOff 这些 Web 用到的图标。
 * 一个 15dp 的字形为它拉一整包矢量不划算，画出来观感一致、体积为零。
 */

/**
 * 「列表 / 四格」两个 15dp 字形。
 *
 * Web 用 lucide 的 `ListOrdered` / `List` / `LayoutGrid`（`pages/Detail.tsx:290-300`、
 * `ChapterDrawer.tsx`、`ReaderShell.tsx:326`）。工程里只引入了 `material-icons-core`
 * （为避免整包 `material-icons-extended` 多出 ~4MB），核心集里既没有 LayoutGrid 也没有
 * List/ListOrdered，于是照 lucide 的造型用 Canvas 画：
 * - 列表 = 左侧三颗小方点 + 三条横线（= lucide `List`；详情页标题旁的 `ListOrdered`
 *   用它代理，少三颗数字，15dp 下分辨不出）；
 * - 四格 = 2×2 描边圆角方块（= lucide `LayoutGrid`）。
 *
 * 两个尺寸参数都按 15dp 的视觉重量调过，放大到 19dp（阅读器工具栏）也不会散。
 */
@Composable
fun ChapterLayoutGlyph(
    grid: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    color: Color = Color.Unspecified,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        if (grid) {
            // LayoutGrid：2×2 方块，间距约为边长的 14%
            val gap = this.size.width * 0.16f
            val cell = (this.size.width - gap) / 2f
            val radius = CornerRadius(1.6f.dp.toPx())
            for (col in 0..1) {
                for (row in 0..1) {
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = radius,
                        style = Stroke(width = 1.4f.dp.toPx()),
                    )
                }
            }
        } else {
            // ListOrdered：三行「点 + 线」
            val lineH = 1.4f.dp.toPx()
            val dot = this.size.width * 0.2f
            val lineStart = this.size.width * 0.34f
            listOf(0.18f, 0.5f, 0.82f).forEach { f ->
                val cy = this.size.height * f
                drawRoundRect(
                    color = c,
                    topLeft = Offset(0f, cy - dot / 2f),
                    size = Size(dot, dot),
                    cornerRadius = CornerRadius(0.8f.dp.toPx()),
                )
                drawRoundRect(
                    color = c,
                    topLeft = Offset(lineStart, cy - lineH / 2f),
                    size = Size(this.size.width - lineStart, lineH),
                    cornerRadius = CornerRadius(lineH / 2f),
                )
            }
        }
    }
}

/**
 * lucide `History` 同构字形（Web 首页「继续阅读」标题前的 15dp 图标，
 * `pages/Home.tsx:228` 用 `History size={15}` + `text-brand-500/80`）。
 *
 * 造型：圆环在左上留一个缺口（回拨箭头的位置）+ 一长一短两根指针 + 缺口处的箭头。
 * 15dp 下细节本来就分辨不出，抓住「带缺口表盘 + 指针」这个剪影即可。
 */
@Composable
fun HistoryGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    color: Color = Color.Unspecified,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        val stroke = 1.4f.dp.toPx()
        val r = (this.size.minDimension - stroke) / 2f
        // 表盘略下移，给缺口处的箭头腾出上边一点点空间
        val center = Offset(this.size.width / 2f, this.size.height / 2f + stroke * 0.5f)
        drawArc(
            color = c,
            startAngle = 150f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // 指针：分针朝上（略偏右），时针朝右下
        drawLine(c, center, Offset(center.x, center.y - r * 0.55f), stroke, StrokeCap.Round)
        drawLine(c, center, Offset(center.x + r * 0.42f, center.y + r * 0.2f), stroke, StrokeCap.Round)
        // 缺口箭头：两笔从弧起点往左上挑，读作「回拨」
        val ax = center.x + r * 0.5f * -0.866f // cos150°
        val ay = center.y - r * 0.5f
        drawLine(c, Offset(ax, ay), Offset(ax, ay - r * 0.34f), stroke, StrokeCap.Round)
    }
}

/**
 * lucide `RotateCcw` 同构字形（详情页「重置进度」按钮，Web `Detail.tsx:277` 用 15px）。
 *
 * 一个逆时针的回旋箭头：360°−60° 的圆弧 + 端头的三角箭头。
 * 「重扫」用的是核心集里的 `Refresh`（顺时针），两者必须有区别 —— 之前两个按钮
 * 都画 `Refresh`，一排按钮里出现两个一模一样的图标。
 */
@Composable
fun RotateCcwGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 1.5.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val r = (this.size.minDimension - stroke) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawArc(
            color = c,
            startAngle = 60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // 箭头：在 60° 那个端点上，两笔成一个朝下的 "v"
        val ex = center.x + r * 0.5f
        val ey = center.y + r * 0.866f
        drawLine(c, Offset(ex, ey), Offset(ex - r * 0.34f, ey), stroke, StrokeCap.Round)
        drawLine(c, Offset(ex, ey), Offset(ex, ey - r * 0.34f), stroke, StrokeCap.Round)
    }
}

/**
 * lucide `ChevronLeft` / `ChevronRight` 同构字形（阅读器工具栏两侧与上下章按钮用）。
 *
 * 为什么自绘：核心图标集里的 `KeyboardArrowLeft/Right` 是**实心三角**箭头，
 * lucide 的 chevron 是两根 2dp 圆头折线 —— 并排看是两种视觉语言。
 * 顶部返回键（20dp）与底部「上一章 / 下一章」（18dp）共用本字形。
 */
@Composable
fun ChevronGlyph(
    left: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Canvas(modifier.size(size)) {
        val side = this.size.width
        val stroke = strokeWidth.toPx()
        // lucide 24 视口里是 (15,18)-(9,12)-(15,6)，等比映射到方框
        val outerX = side * if (left) 0.625f else 0.375f
        val innerX = side * if (left) 0.375f else 0.625f
        val topY = side * 0.25f
        val midY = side * 0.5f
        val bottomY = side * 0.75f
        drawLine(c, Offset(outerX, topY), Offset(innerX, midY), stroke, StrokeCap.Round)
        drawLine(c, Offset(innerX, midY), Offset(outerX, bottomY), stroke, StrokeCap.Round)
    }
}

/**
 * lucide `ChevronsLeft` / `ChevronsRight` 同构字形（两个叠放的 chevron）。
 *
 * 阅读器的「到头」提示用它表示方向：Web `ReaderShell.tsx:405-406` 用
 * `ChevronsLeft/Right size={14}` —— 单 chevron 语义是「上一页/下一页」，
 * 双 chevron 才是「已经到边了」。核心图标集里没有，自绘。
 */
@Composable
fun ChevronsGlyph(
    left: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 1.8.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Canvas(modifier.size(size)) {
        val side = this.size.width
        val stroke = strokeWidth.toPx()
        // lucide 24 视口：右向是 (13,17)-(18,12)-(13,7) 与 (6,17)-(11,12)-(6,7)
        val xs = if (left) {
            listOf(0.667f to 0.290f, 0.417f to 0.479f) // 靠离折点分别在外/内
        } else {
            listOf(0.333f to 0.710f, 0.583f to 0.521f)
        }
        val top = side * 0.29f
        val mid = side * 0.5f
        val bottom = side * 0.71f
        xs.forEach { (a, b) -> drawLine(c, Offset(a * side, top), Offset(b * side, mid), stroke, StrokeCap.Round); drawLine(c, Offset(b * side, mid), Offset(a * side, bottom), stroke, StrokeCap.Round) }
    }
}

/**
 * lucide `Settings2` 同构字形（阅读器右上角的设置入口）。
 *
 * 为什么不用 `Icons.Filled.Settings`：那是**实心齿轮**，而阅读器顶栏另外两个图标
 * （返回的 ChevronLeft、章节目录的 List）都是 2dp 圆头细线稿 —— 一个实心齿轮夹在中间
 * 明显不是同一套视觉语言。Web 这里用的正是 lucide 的 `Settings2`
 * （两条横轨 + 两个可拖动的圆，`ReaderShell.tsx:332`，19px）。
 */
@Composable
fun Settings2Glyph(
    modifier: Modifier = Modifier,
    size: Dp = 19.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = strokeWidth.toPx()
        // 上行：(11,7)-(20,7) + 圆 (7,7) r=3
        drawLine(c, Offset(11f * u, 7f * u), Offset(20f * u, 7f * u), stroke, StrokeCap.Round)
        drawCircle(c, radius = 3f * u, center = Offset(7f * u, 7f * u), style = Stroke(stroke))
        // 下行：(14,17)-(5,17) + 圆 (17,17) r=3
        drawLine(c, Offset(14f * u, 17f * u), Offset(5f * u, 17f * u), stroke, StrokeCap.Round)
        drawCircle(c, radius = 3f * u, center = Offset(17f * u, 17f * u), style = Stroke(stroke))
    }
}

/**
 * lucide `X` 同构字形（阅读器续读提示卡右上角的关闭，Web `ReaderPrompt.tsx:122` 用 13px）。
 *
 * 核心图标集里其实有 `Icons.Filled.Close`，但它按 24dp 视口画、线宽固定，
 * 缩到 13dp 时笔画会糊；自绘能直接指定 1.6dp 的线宽与圆头，13dp 下依然清楚。
 */
@Composable
fun CloseGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 13.dp,
    color: Color = Color.Unspecified,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        val stroke = 1.6f.dp.toPx()
        val pad = stroke * 1.2f
        drawLine(
            color = c,
            start = Offset(pad, pad),
            end = Offset(this.size.width - pad, this.size.height - pad),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = c,
            start = Offset(this.size.width - pad, pad),
            end = Offset(pad, this.size.height - pad),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * lucide `ImageOff` 同构字形（阅读器页图失败态的 22dp 图标，Web `page-image.tsx:172`、
 * `pagers.tsx:332/576/776` 用 `ImageOff size={22}` + `text-white/35`）。
 *
 * 造型：描边画框 + 左下到右上的「山形」折线 + 左上角的小圆（太阳）+ 一道贯穿的对角斜杠。
 * 22dp 下按 lucide 的 24 视口等比缩放：画框占 12.5%..87.5%，斜杠略微出头。
 */
@Composable
fun ImageOffGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = Color.Unspecified,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        // lucide 24 视口 → 归一化：线宽 2/24 ≈ 0.083，画框 3..21 = 0.125..0.875
        val stroke = s * 0.083f
        val left = s * 0.125f
        val right = s * 0.875f
        drawRoundRect(
            color = c,
            topLeft = Offset(left, left),
            size = Size(right - left, right - left),
            cornerRadius = CornerRadius(s * 0.083f),
            style = Stroke(width = stroke),
        )
        // 山形：从画框左下角往右上折两段（lucide 的 `m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21`）
        val bottom = s * 0.875f
        drawLine(c, Offset(left, bottom), Offset(s * 0.46f, s * 0.52f), stroke, StrokeCap.Round)
        drawLine(c, Offset(s * 0.46f, s * 0.52f), Offset(s * 0.60f, s * 0.66f), stroke, StrokeCap.Round)
        // 太阳：lucide `circle cx=9 cy=9 r=2` → 0.375 / r 0.083
        drawCircle(
            color = c,
            radius = s * 0.083f,
            center = Offset(s * 0.375f, s * 0.375f),
            style = Stroke(width = stroke),
        )
        // 对角斜杠：唯一表达「off」的那一笔，比画框描边略粗才压得住
        val slash = s * 0.06f
        drawLine(
            color = c,
            start = Offset(slash, slash),
            end = Offset(s - slash, s - slash),
            strokeWidth = stroke * 1.2f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * lucide `Tag` 同构字形（详情页标签行前的 12px 图标，Web `Detail.tsx:204`）。
 *
 * lucide 的 Tag 是「一个缺角方块 + 一个圆点」：把方块转了 45°，缺的那一角朝右下拖出一条尖。
 * 12dp 下画完整路径只剩糊，取剪影：转 45° 的描边方块 + 一个实心点。
 */
@Composable
fun TagGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    color: Color = Color.Unspecified,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        val stroke = 1.2f.dp.toPx()
        val s = this.size.minDimension
        val pad = s * 0.13f
        // 45° 的方块用「圆角矩形 + rotate」画，比手算四条线稳
        rotate(degrees = 45f, pivot = Offset(s / 2f, s / 2f)) {
            drawRoundRect(
                color = c,
                topLeft = Offset(pad, s * 0.28f),
                size = Size(s - pad * 2f, s * 0.44f),
                cornerRadius = CornerRadius(s * 0.09f),
                style = Stroke(width = stroke),
            )
        }
        // 穿绳孔：Web 是左上角那个小圆
        drawCircle(color = c, radius = s * 0.055f, center = Offset(s * 0.36f, s * 0.36f))
    }
}

/**
 * lucide `AlertCircle`（圆圈叹号）：提示条里的「失败」图标（Web `toast.tsx` 的
 * `AlertCircle size={15} className="text-red-400"`）。
 *
 * 核心图标集里没有它 —— `Icons.Filled.Warning` 是实心三角、`Icons.Filled.Error`
 * 在 extended 里；实心三角与 lucide 的 2dp 圆头线稿不是一套视觉语言，所以自绘。
 */
@Composable
fun AlertCircleGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    color: Color = Color.Unspecified,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.error else color
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        // 24 视口等比缩放：圆环 r=9.6、竖线 7.2→13、底点 r=1.1
        val u = s / 24f
        val stroke = 1.8f * u
        drawCircle(
            color = c,
            radius = 9.6f * u,
            center = Offset(s / 2f, s / 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = c,
            start = Offset(s / 2f, 7.2f * u),
            end = Offset(s / 2f, 13f * u),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(color = c, radius = 1.1f * u, center = Offset(s / 2f, 16.4f * u))
    }
}

/**
 * lucide `ArrowUp`（细线向上箭头）：回到顶部按钮（Web `BackToTop.tsx` 的
 * `<ArrowUp size={18} />`）。
 *
 * 同样是自绘而不是用 `Icons.Filled.KeyboardArrowUp`：后者是**实心三角**，
 * 和按钮本身的深色玻璃圆 + 其它 lucide 线稿图标不是一套语言。
 * 造型 = 一条竖线（12,19 → 12,5）+ 一个向上折角（5,12 → 12,5 → 19,12）。
 */
@Composable
fun ArrowUpGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Canvas(modifier.size(size)) {
        val s = this.size.width
        val stroke = strokeWidth.toPx()
        val cx = s * 0.5f
        drawLine(
            color = c,
            start = Offset(cx, s * 19f / 24f),
            end = Offset(cx, s * 5f / 24f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = c,
            start = Offset(s * 5f / 24f, s * 12f / 24f),
            end = Offset(cx, s * 5f / 24f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = c,
            start = Offset(cx, s * 5f / 24f),
            end = Offset(s * 19f / 24f, s * 12f / 24f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
