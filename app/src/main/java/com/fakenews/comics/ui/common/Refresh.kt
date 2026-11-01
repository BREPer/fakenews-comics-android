package com.fakenews.comics.ui.common

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import kotlinx.coroutines.delay

/**
 * 下拉刷新容器（首页 / 书架 / 漫画库共用）。
 *
 * 为什么不直接用 M3 的默认指示器：它的内容层是 `CircularArrowProgressIndicator`
 * —— 一根弧线 + 一枚绕着圈跑的箭头，弧线的起止角由 `rememberInfiniteTransition`
 * 的 pivot 偏移拼出来。本地接口几十毫秒就返回时，它刚起转就被摘掉，实测箭头会停在
 * 半圈不动（看起来像卡住）。换成「拉动进度弧 + 确定态圆环」后没有会卡住的中间态。
 *
 * 另加一段最短显示时长：数据一回来就立刻摘指示器，用户读到的是「闪了一下、什么都没
 * 发生」。保持期内网格其实已经换成新数据了，所以这不拖慢任何反馈。
 *
 * **调用方约束**：`onRefresh` 必须让 `isRefreshing` 真的翻上去。M3 的
 * `PullToRefreshElement` 是 data class，`isRefreshing` 没变就不调 `update()`，而
 * `update()` 是指示器收回的唯一入口 —— 松手后它把圆底钉在阈值处等这个标志位，
 * 等不到就永远停在那儿（首页空列表曾经这样）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    var showRefreshing by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            startedAt = SystemClock.elapsedRealtime()
            showRefreshing = true
        } else if (showRefreshing) {
            val waited = SystemClock.elapsedRealtime() - startedAt
            if (waited < MinRefreshVisibleMs) delay(MinRefreshVisibleMs - waited)
            showRefreshing = false
        }
    }
    PullToRefreshBox(
        isRefreshing = showRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            RefreshIndicator(
                state = state,
                isRefreshing = showRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/**
 * 自绘指示器（Web 没有这个手势，口径按 App 自己的卡片语言来）：
 * 圆底与提示条（`ToastRow`）同一块材质 —— `ink900`·0.94 + 1dp `ink800`·0.70 发丝描边。
 * 真正的背景模糊拿不到：指示器正好长在 Haze 源的子树里，当不了自己的 child，
 * 另起一个 HazeState 等于给这屏再加一层整页图层。
 * 拉动时一圈品牌色进度弧从 12 点顺时针长出，中间一枚人字箭头在未达阈值时朝下、
 * 过阈值翻成朝上（= 松手即刷新）；刷新中箭头与进度弧淡出，换成匀速圆环。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    // 只把「过没过阈值」这个布尔送进组合：distanceFraction 每帧都变，直接读会让指示器
    // 跟着逐帧重组（derivedStateOf 把它压成一次翻转）
    val passed = remember { derivedStateOf { state.distanceFraction >= 1f } }
    // 箭头 ↔ 圆环的交接（150ms，与 Web 的颜色过渡同档）
    val spin by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else 0f,
        animationSpec = tween(150, easing = EaseOutQuint),
        label = "refreshSpin",
    )
    // 翻转按「时间」走、不按拉动量走：拉动量驱动的话，停在阈值中段时箭头会稳定指着右边，
    // 读起来像个播放键。现在只有「没过阈值=朝下」「过了=朝上」两个稳态，中间 180ms 翻过去。
    val flip by animateFloatAsState(
        targetValue = if (passed.value) 0f else 1f,
        animationSpec = tween(180, easing = EaseOutQuint),
        label = "refreshFlip",
    )
    Box(
        modifier = modifier
            .size(40.dp)
            .pullToRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                containerColor = ink.ink900.copy(alpha = 0.94f),
                // M3 默认 6dp 是「贴了块塑料片」的另一半；2dp 才接近卡片那层 shadow-soft 的贴地感
                elevation = 2.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 边界改由 1dp 发丝描边来定义（与 ToastRow 的 ring 同色同宽）。半径收进半个描边宽，
            // 免得被 container 自己的 CircleShape 削掉一半
            val rim = 1.dp.toPx()
            drawCircle(
                color = ink.ink800.copy(alpha = 0.70f),
                radius = size.minDimension / 2f - rim / 2f,
                center = center,
                style = Stroke(width = rim),
            )
            val fraction = state.distanceFraction.coerceIn(0f, 1f)
            if (fraction > 0.02f && spin < 0.98f) {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2f + 1.5.dp.toPx()
                drawArc(
                    color = brand.brand500.copy(alpha = 1f - spin),
                    startAngle = -90f,
                    sweepAngle = 330f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        /*
         * 只有「正在刷新」或「刚结束的 150ms 淡出」才组合转圈。
         *
         * M3 的不确定进度圈内部是 `rememberInfiniteTransition` 的无限旋转：**只要它在组合里
         * 就一直请求下一帧，不管 alpha 是不是 0**。这个指示器常驻在三个列表页（首页/书架/
         * 漫画库），于是页面停稳、画面逐像素完全没有变化时，仍然每秒跑 60 次
         * performTraversals + 整页重画 —— 模拟器实测两帧截图差异 0 像素，但 2 秒 120 帧；
         * 同样的三个页面切到没用下拉刷新的设置页，静置帧数是 0。
         * 代价是静止时白耗电、GPU 常忙，真机上还压着滚动的帧预算。
         *
         * 用 `spin` 作淡出条件：刷新结束那一刻 spin 从 1→0 走 150ms，走完才摘掉。
         * 这里是组合阶段读 spin，只让这个小指示器在 150ms 内重组，比整页空转便宜得多。
         */
        if (isRefreshing || spin > 0.02f) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp).graphicsLayer { alpha = spin },
                color = brand.brand500,
                strokeWidth = 2.5.dp,
                trackColor = Color.Transparent,
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = ink.ink400,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    rotationZ = 180f * flip
                    // 刚冒头时圆底只露一条边，箭头先淡出，避免和圆底边缘挤成一团
                    alpha = (1f - spin) *
                        ((state.distanceFraction - 0.12f) / 0.22f).coerceIn(0f, 1f)
                },
        )
    }
}

/** 指示器最少显示时长：本地快响应下也要让用户看清「刷新过了」 */
private const val MinRefreshVisibleMs = 400L
