package com.fakenews.comics.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 一组 Lazy 行拼成的「一张 `.card`」：首行画上边框 + 上圆角，中间行只画左右竖边 +
 * 一条 `ink-800` 分隔线，末行画下边框 + 下圆角。
 *
 * 记录列表（书架）与章节列表（详情）在 Web 上都是 `card divide-y` 一个容器，
 * 但 Compose 的 LazyColumn 没法让一个容器跨 item 包住内容（容器会失去 lazy，
 * 几百行会被一次性组合）。这里把卡片的四条边拆到每行各自绘制，拼接后与
 * 一整张卡看不出差别，行仍然是独立 item。
 *
 * 卡片外阴影没有搬过来：逐行画 shadow 会在行与行之间叠出内阴影。
 * 这是已知差异，见 `docs/2026-10-27-AI接手说明.md` §8。
 */
@Composable
fun GroupedCardRow(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val shape = RoundedCornerShape(
        topStart = if (isFirst) 16.dp else 0.dp,
        topEnd = if (isFirst) 16.dp else 0.dp,
        bottomStart = if (isLast) 16.dp else 0.dp,
        bottomEnd = if (isLast) 16.dp else 0.dp,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ink.ink900)
            .drawBehind {
                val w = 1.dp.toPx()
                val border = brand.cardBorder
                val half = w / 2f
                drawLine(border, Offset(half, 0f), Offset(half, size.height), w)
                drawLine(
                    border,
                    Offset(size.width - half, 0f),
                    Offset(size.width - half, size.height),
                    w,
                )
                if (isFirst) {
                    drawLine(border, Offset(0f, half), Offset(size.width, half), w)
                }
                drawLine(
                    color = if (isLast) border else ink.ink800,
                    start = Offset(0f, size.height - half),
                    end = Offset(size.width, size.height - half),
                    strokeWidth = w,
                )
            },
    ) {
        content()
    }
}
