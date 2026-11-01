package com.fakenews.comics.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 底栏用的两个自绘字形（对齐 lucide `BookMarked` / `Library`）。
 *
 * 起因与 ChapterGlyph 相同：工程只引 `material-icons-core`，核心集里没有这两个造型 ——
 * 能凑的近似物（Bookmark=纯丝带、Menu=三条横线）并排放在底栏里会读成另一种语义。
 * 底栏是常驻元素，图标的「剪影」比细节重要，按 lucide 的 24 视口等比缩放即可。
 */

/**
 * lucide `BookMarked` 同构字形（Web 底栏「书架」）。
 *
 * 造型 = 一本立着的书（外框带圆角）+ 垂在书脊处的一根书签丝带。
 * 20dp 下丝带的 V 形缺口刚好还剩两个像素，能读成「书签」而不是一条竖线。
 */
@Composable
fun BookMarkedGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 1.8.dp,
) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        val u = side / 24f // lucide 的 24 视口单位
        val stroke = strokeWidth.toPx()
        val tint = color.takeIf { it != Color.Unspecified } ?: Color.Black

        // 书的外框：留出左侧书脊的呼吸位，右下无圆角（lucide 只在左下倒角）
        drawRoundRect(
            color = tint,
            topLeft = Offset(5f * u, 2.5f * u),
            size = Size(14f * u, 19f * u),
            cornerRadius = CornerRadius(2f * u, 2f * u),
            style = Stroke(width = stroke),
        )
        // 书签丝带：从顶边垂下、下探 7u，中间收出一个 V
        val ribbon = Path().apply {
            moveTo(9.5f * u, 2.5f * u)
            lineTo(9.5f * u, 10f * u)
            lineTo(12f * u, 7.6f * u)
            lineTo(14.5f * u, 10f * u)
            lineTo(14.5f * u, 2.5f * u)
        }
        drawPath(
            path = ribbon,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * lucide `Library` 同构字形（Web 底栏「漫画库」）。
 *
 * 造型 = 三本高度递增的竖书 + 右侧一本斜靠的书。四条线都是 2dp 圆头，
 * 斜线角度照抄 lucide 的 `M16 6l4 14`（约 16° 右倾），歪太多会读成「书架倒了」。
 */
@Composable
fun LibraryGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 1.8.dp,
) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        val u = side / 24f
        val stroke = strokeWidth.toPx()
        val tint = color.takeIf { it != Color.Unspecified } ?: Color.Black
        val cap = StrokeCap.Round

        // 三本竖书：x = 4 / 8 / 12，从各自顶端落到同一基线 y=20
        drawLine(tint, Offset(4f * u, 4f * u), Offset(4f * u, 20f * u), stroke, cap)
        drawLine(tint, Offset(8f * u, 8f * u), Offset(8f * u, 20f * u), stroke, cap)
        drawLine(tint, Offset(12f * u, 6f * u), Offset(12f * u, 20f * u), stroke, cap)
        // 斜靠的一本：底端与其余书齐平，顶端向右上探出
        drawLine(tint, Offset(16f * u, 6f * u), Offset(20f * u, 20f * u), stroke, cap)
    }
}
