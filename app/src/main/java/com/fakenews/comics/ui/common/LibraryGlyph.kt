package com.fakenews.comics.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 漫画库管理面用到的几个 lucide 字形（2026-10-28「Android 漫画库全量对齐 Web」时补）。
 *
 * 与 ui/common/ChapterGlyph.kt 同一套做法：工程只引 material-icons-core，
 * 而 Folder / FolderOpen / Trash2 / CheckCircle2 都不在核心集里，为一个 15dp 的图标
 * 拉整包 material-icons-extended（~4MB）不划算。
 */

/**
 * lucide `Folder` / `FolderOpen` 的折线版。
 *
 * 两个都只是一条闭合折线：把 24 视口里的关键点等比缩到 [size]，小圆弧用直线近似 ——
 * 16dp 下那点弧度分辨不出，反而 [StrokeJoin.Round] 的倒角更好读。
 * [open] 用来表示「已勾选为库路径」（Web 的行尾按钮是 Folder ↔ FolderOpen 切换）。
 */
@Composable
fun FolderGlyph(
    open: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 1.6.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    val pts = if (open) OPEN_POINTS else CLOSED_POINTS
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val path = Path().apply {
            var first = true
            pts.forEach { (x, y) ->
                if (first) {
                    moveTo(x * u, y * u)
                    first = false
                } else {
                    lineTo(x * u, y * u)
                }
            }
            close()
        }
        drawPath(
            path = path,
            color = c,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/** lucide `FolderPlus`：目录 + 中间一个加号（新建漫画库按钮）。 */
@Composable
fun FolderPlusGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 1.6.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val path = Path().apply {
            var first = true
            CLOSED_POINTS.forEach { (x, y) ->
                if (first) {
                    moveTo(x * u, y * u)
                    first = false
                } else {
                    lineTo(x * u, y * u)
                }
            }
            close()
        }
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawPath(path = path, color = c, style = stroke)
        // lucide 的加号：竖线 (12,11.25)-(12,16.75)、横线 (9.25,14)-(14.75,14)
        drawLine(c, Offset2(12f, 11.25f, u), Offset2(12f, 16.75f, u), strokeWidth.toPx(), StrokeCap.Round)
        drawLine(c, Offset2(9.25f, 14f, u), Offset2(14.75f, 14f, u), strokeWidth.toPx(), StrokeCap.Round)
    }
}

/** lucide `Trash2`（删除漫画库）：顶盖横杆 + 桶身 + 盖提手 + 两道竖纹。 */
@Composable
fun TrashGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 1.6.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val sw = strokeWidth.toPx()
        // 顶盖横杆：(3,6)-(21,6)
        drawLine(c, Offset2(3f, 6f, u), Offset2(21f, 6f, u), sw, StrokeCap.Round)
        // 桶身：(19,6)-(19,20)-(17,22)-(7,22)-(5,20)-(5,6)
        drawPath(
            path = Path().apply {
                moveTo(19f * u, 6f * u)
                lineTo(19f * u, 20f * u)
                lineTo(17f * u, 22f * u)
                lineTo(7f * u, 22f * u)
                lineTo(5f * u, 20f * u)
                lineTo(5f * u, 6f * u)
            },
            color = c,
            style = stroke,
        )
        // 盖提手：(8,6)-(8,4)-(10,2)-(14,2)-(16,4)-(16,6)
        drawPath(
            path = Path().apply {
                moveTo(8f * u, 6f * u)
                lineTo(8f * u, 4f * u)
                lineTo(10f * u, 2f * u)
                lineTo(14f * u, 2f * u)
                lineTo(16f * u, 4f * u)
                lineTo(16f * u, 6f * u)
            },
            color = c,
            style = stroke,
        )
        // 两道竖纹（x=10 / x=14，y 11→17）
        drawLine(c, Offset2(10f, 11f, u), Offset2(10f, 17f, u), sw, StrokeCap.Round)
        drawLine(c, Offset2(14f, 11f, u), Offset2(14f, 17f, u), sw, StrokeCap.Round)
    }
}

/** lucide `Upload`：一个开口的托盘 + 向上箭头（上传到库）。 */
@Composable
fun UploadGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 1.6.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val sw = strokeWidth.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // 托盘：(21,15)-(21,19)-(19,21)-(5,21)-(3,19)-(3,15)
        drawPath(
            path = Path().apply {
                moveTo(21f * u, 15f * u)
                lineTo(21f * u, 19f * u)
                lineTo(19f * u, 21f * u)
                lineTo(5f * u, 21f * u)
                lineTo(3f * u, 19f * u)
                lineTo(3f * u, 15f * u)
            },
            color = c,
            style = stroke,
        )
        // 箭头：(17,8)-(12,3)-(7,8) + 竖线 (12,3)-(12,15)
        drawPath(
            path = Path().apply {
                moveTo(17f * u, 8f * u)
                lineTo(12f * u, 3f * u)
                lineTo(7f * u, 8f * u)
            },
            color = c,
            style = stroke,
        )
        drawLine(c, Offset2(12f, 3f, u), Offset2(12f, 15f, u), sw, StrokeCap.Round)
    }
}

/** lucide `CheckCircle2`：上传完成的那个绿色圆环勾。 */
@Composable
fun CheckCircleGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
) {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val sw = strokeWidth.toPx()
        drawCircle(
            color = c,
            radius = 9.6f * u,
            center = Offset2(12f, 12f, u),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        drawPath(
            path = Path().apply {
                moveTo(7.6f * u, 12.4f * u)
                lineTo(10.8f * u, 15.6f * u)
                lineTo(16.6f * u, 8.6f * u)
            },
            color = c,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 缩放 + 24 视口 → 像素坐标的小工具：写 `Offset2(x, y, u)` 比每次 `.dp.toPx()` 清楚。 */
private fun Offset2(x: Float, y: Float, u: Float): Offset = Offset(x * u, y * u)

/** 闭合目录（lucide `folder`）的关键点。 */
private val CLOSED_POINTS = listOf(
    20f to 20f, 22f to 18f, 22f to 8f, 20f to 6f,
    12.1f to 6f, 10.41f to 5.1f, 9.6f to 3.9f, 7.93f to 3f,
    4f to 3f, 2f to 5f, 2f to 18f, 4f to 20f,
)

/** 打开目录（lucide `folder-open`）的关键点。 */
private val OPEN_POINTS = listOf(
    6f to 14f, 7.45f to 11.1f, 9.24f to 10f, 20f to 10f,
    21.94f to 12.5f, 20.39f to 18.5f, 18.45f to 20f, 4f to 20f,
    2f to 18f, 2f to 5f, 4f to 3f, 7.9f to 3f,
    9.59f to 3.9f, 10.4f to 5.1f, 12.07f to 6f, 18f to 6f,
    20f to 8f, 20f to 10f, 6f to 10f,
)
