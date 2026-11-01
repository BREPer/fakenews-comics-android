package com.fakenews.comics.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.fakenews.comics.ui.LocalBottomBarTopInset
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 回顶按钮的出现阈值（dp）。Web `BackToTop.tsx` 用的是 `scrollY > min(innerHeight, 480)`：
 * 移动端 viewport 的 CSS px 与 dp 是 1:1（`width=device-width`），所以这里同样是 480。
 *
 * 不用「1 屏」是因为桌面宽屏下首屏卡片排成多列、可滚动距离不到一屏，按钮永远不会出现；
 * 也不用「已滚过的比例」，无限滚动会把文档越滚越高、阈值跟着涨，按钮会来回抖。
 */
val BACK_TO_TOP_AFTER = 480.dp

/**
 * 回到顶部（逐值对齐 Web `components/layout/BackToTop.tsx`）。
 *
 * 几处容易写错的地方：
 *   - **常驻挂载、只切样式**：Web 的注释写明「滚回顶 → 又滚下来」是最常见用法，
 *     条件渲染会让这个来回每次都重播入场动画。隐藏时 [pressClickable] 的 enabled
 *     置 false，等价于 Web 的 `pointer-events-none` + `tabIndex=-1`；
 *   - 收起时**下移 8dp 再淡掉**：只有透明度变化看起来是「原地淡掉」而不是「沉下去」；
 *   - 44dp 正圆 + `bg-ink-900/85` + `ring-1 ring-ink-800`：不是 M3 的
 *     FloatingActionButton（56dp 圆角方 + primaryContainer 浅底，与深色玻璃圆是两种观感）；
 *   - 落点：Web 是 `bottom-[calc(5.5rem+safe-area)]`，即**底栏之上再留约 30px**。
 *     调用方 Box 的底边是**屏幕底边** —— 底栏浮在内容上、不占布局 —— 所以要叠上
 *     [LocalBottomBarTopInset]（栏顶边到屏幕底边，含手势区）。只留 12dp 会把按钮
 *     整个塞进栏的毛玻璃后面：看不见、点不着，看起来像没有这个功能（首页踩过）。
 */
@Composable
fun BackToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(200, easing = EaseOutQuint),
        label = "backToTopAlpha",
    )
    val shift by animateDpAsState(
        targetValue = if (visible) 0.dp else 8.dp,
        animationSpec = tween(200, easing = EaseOutQuint),
        label = "backToTopShift",
    )
    Box(
        modifier
            .padding(end = 16.dp, bottom = 12.dp + LocalBottomBarTopInset.current)
            .size(44.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = shift.toPx()
            }
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .clip(CircleShape)
            .background(ink.ink900.copy(alpha = 0.85f))
            .border(1.dp, ink.ink800, CircleShape)
            .pressClickable(
                enabled = visible,
                pressedScale = 0.90f,
                onClickLabel = "回到顶部",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ArrowUpGlyph(size = 18.dp, color = ink.ink200)
    }
}

/**
 * 「是否已滚过回顶阈值」，懒网格版。
 *
 * LazyGridState 只暴露「首项下标 + 首项内偏移」，**滚过一项之后拿不到前面项的高度**，
 * 单看 offset 在小格子上永远到不了 480dp（一格才 200 多 dp，偏移每到项尾就归零），
 * 所以这里自己累加滚动距离：
 *   - 回到第 0 项：直接用 offset，精确，误差不累积；
 *   - 同一项内：offset 差，精确；
 *   - 滚过一项：上一项高度 − 上次偏移 + 新偏移（首项还在视口里时它的高度是现成的）；
 *   - 跨多项（scrollToItem / 快速滑动）：按项高 × 项数估计，够阈值判断用。
 */
@Composable
fun rememberBackToTopVisible(state: LazyGridState): Boolean =
    rememberPastThreshold(state) {
        // 网格项的 size 是 IntSize（宽高都有），列表项是 Int（只有高度）
        Triple(
            state.firstVisibleItemIndex,
            state.firstVisibleItemScrollOffset,
            state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height,
        )
    }

/** 懒列表版，见 [rememberBackToTopVisible]。 */
@Composable
fun rememberBackToTopVisible(state: LazyListState): Boolean =
    rememberPastThreshold(state) {
        Triple(
            state.firstVisibleItemIndex,
            state.firstVisibleItemScrollOffset,
            state.layoutInfo.visibleItemsInfo.firstOrNull()?.size,
        )
    }

/** 供 [rememberBackToTopVisible] 读取：首项下标、首项内偏移、首项高度（可能为空）。 */
private typealias ScrollRead = Triple<Int, Int, Int?>

@Composable
private fun <T> rememberPastThreshold(state: T, read: (T) -> ScrollRead): Boolean {
    val density = LocalDensity.current
    val threshold = remember(density) { with(density) { BACK_TO_TOP_AFTER.toPx() }.toInt() }
    val total = remember { mutableIntStateOf(0) }
    LaunchedEffect(state, threshold) {
        var lastIndex = 0
        var lastOffset = 0
        var lastSize = 0
        // 首次发射可能是「恢复滚动位置后直接落在列表中段」：这时没有历史可累加，
        // 只能按平均项高估一个 —— 宁可高估，否则返回一个已滚了半页的页面按钮不出现。
        var first = true
        snapshotFlow { read(state) }.collect { (idx, off, size) ->
            val itemSize = size ?: lastSize
            total.intValue += when {
                first && idx > 0 -> maxOf(idx * itemSize + off, threshold + 1)
                idx == 0 -> off - total.intValue // 重置成精确值
                idx == lastIndex -> off - lastOffset
                idx == lastIndex + 1 -> lastSize - lastOffset + off
                idx == lastIndex - 1 -> lastOffset - off - itemSize
                else -> (idx - lastIndex) * itemSize
            }
            total.intValue = total.intValue.coerceAtLeast(0)
            first = false
            lastIndex = idx
            lastOffset = off
            lastSize = itemSize
        }
    }
    val past by remember(threshold) { derivedStateOf { total.intValue > threshold } }
    return past
}
