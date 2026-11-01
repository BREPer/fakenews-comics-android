package com.fakenews.comics.ui.reader

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.fakenews.comics.ui.common.bezierEasing
import com.fakenews.comics.ui.theme.LocalBrandColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/* ================= 平移翻页参数（对齐 Web 端 pan-track.ts） ================= */

/**
 * 点击/按键翻页的整页位移时长（拆分模式的半页平移也用它，保持整屏位移手感一致）。
 * ReaderPages.kt 的 SplitPage 也读它，所以是 internal。
 */
internal const val PAN_MS = 260

/** ≈ easeOutQuad：起步快、收尾稳。整屏位移用极端 ease-out 会「窜出去再慢慢爬」。 */
internal val PanEase = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

/**
 * 越界回弹：更慢、带一点过冲（back-out），做出「被纸边顶住又弹回来」的重量感。
 *
 * 走 [bezierEasing] 而不是 `CubicBezierEasing`：y1 = 1.24 越界，后者的牛顿迭代在
 * fraction→1 时会无解并抛异常（实测调大 animator_duration_scale 后必崩）。
 */
private const val PAN_SETTLE_MS = 380
private val PanSettleEase = bezierEasing(0.22f, 1.24f, 0.36f, 1f)

/** 松手时位移超过页宽的这个比例就翻页 */
private const val PAN_COMMIT_RATIO = 0.18f

/** 或者甩动速度超过这个值也算翻页（px/ms） */
private const val PAN_COMMIT_VELOCITY = 0.5f

/** 已到首/末页还继续拖时的阻尼（橡皮筋） */
private const val PAN_RUBBER = 0.3f

/** 橡皮筋最多能拉开多少（按页宽计）= 越界上限 × 阻尼，边缘光的亮度按它归一 */
private const val EDGE_PULL_MAX = 0.55f * PAN_RUBBER

/** 越界位移达到这个比例才算「确实顶到了」，否则轻碰一下不弹提示 */
private const val EDGE_TELL_RATIO = 0.3f

/** 松手后边界柔光的收光时长（Web `.pan-edge-glow` 的 `transition: opacity .34s`） */
private const val GLOW_FADE_MS = 340

/**
 * 拖动尾巴上的那次 tap 必须吞掉 —— 不然一次拖动会「翻两页」或「多播一遍点击平移」。
 *
 * 成因是 Compose 的手势分发顺序：`detectTapGestures` 挂在**内层**（页面内容），
 * `draggable` 挂在**外层**（轨道容器），而 Main pass 是叶→根。内层先看到 UP 事件，
 * 此时 UP 还没被拖动消费，`waitForUpOrCancellation()` 判定成功 → 触发 onTap。
 * 拖动明明已经消费了前面所有 MOVE，但 UP 这一帧内层先跑，拦不住。
 *
 * Web 端有同一个坑（浏览器在 pointerup 后照样派发 click），用 `dragClickGuard`
 * 打一个 350ms 窗口显式吞掉。这里照搬，且**只吞一次**：消费后立刻清零，
 * 窗口期内用户真的再点一下不会被误吞。
 */
internal object PanDragGuard {
    private const val WINDOW_MS = 350L
    private var until = 0L

    /** 真实拖动过（超过 touch slop）时调用 */
    fun arm() {
        until = SystemClock.uptimeMillis() + WINDOW_MS
    }

    /** 返回 true = 这次 tap 是拖动的尾巴，调用方应直接忽略 */
    fun consumeTail(): Boolean {
        if (until == 0L) return false
        val now = SystemClock.uptimeMillis()
        val isTail = now < until
        until = 0L
        return isTail
    }
}

/* ================= 平移轨道（胶片式） ================= */

/**
 * 相邻页始终挂在同一条轨道上，拖动只移动轨道本身。
 *
 * 为什么不用「旧页滑出层 + 新页滑入层」两个独立动画层：两层各跑各的动画，进度对不齐，
 * 观感是「两张图各走各的」；轨道法把同一次布局、同一个合成层、同一段位移绑在一起。
 *
 * 位置用「槽位」计量：1 = 当前页居中；0 / 2 = 左右槽位居中。
 *
 * @param pageIndex **模式页**索引
 * @param onCommit 滑到位后提交页码（索引空间，不含阅读方向）
 * @param onEdge 越界：返回是否换了章
 */
@Composable
fun PanTrack(
    pageIndex: Int,
    count: Int,
    rtl: Boolean,
    resetKey: Any,
    canEdgePrev: Boolean,
    canEdgeNext: Boolean,
    gesturesEnabled: Boolean,
    zoomActive: () -> Boolean = { false },
    onCommit: (Int) -> Unit,
    onEdge: (forward: Boolean) -> Boolean,
    /** 整本到头、换不了章：交给外层播「整页回弹」（两条手势路径共用同一份动画） */
    onBlocked: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    slot: @Composable (page: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()

    /** 渲染用的页码：与外层 pageIndex 不同步的那一小段就是滑行动画 */
    var shown by remember(resetKey) { mutableIntStateOf(pageIndex) }
    /** 槽位位置：只被 draw 阶段读取，拖动/动画每帧不触发重组 */
    val pos = remember(resetKey) { mutableFloatStateOf(1f) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    var pendingCommit by remember { mutableStateOf<Int?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var lastReset by remember { mutableStateOf(resetKey) }

    // 边界柔光：side 是物理侧（-1 左 / +1 右），pull 0..1 由拖动直接写，draw 阶段读取
    var glowSide by remember { mutableIntStateOf(0) }
    val pull = remember { mutableFloatStateOf(0f) }
    var glowJob by remember { mutableStateOf<Job?>(null) }

    val slots = remember(shown, count, rtl) {
        val raw = if (rtl) listOf(shown + 1, shown, shown - 1) else listOf(shown - 1, shown, shown + 1)
        raw.map { if (it in 0 until count) it else -1 }
    }
    val hasLeft = slots[0] >= 0
    val hasRight = slots[2] >= 0
    /** 左槽位是不是「下一页」：rtl 时槽位镜像，同一侧在两种阅读方向下对应相反的翻页方向 */
    val leftIsNext = rtl
    val canGoLeft = hasLeft || (if (leftIsNext) canEdgeNext else canEdgePrev)
    val canGoRight = hasRight || (if (leftIsNext) canEdgePrev else canEdgeNext)

    fun animatePos(target: Float, durationMs: Int, easing: Easing, onEnd: (() -> Unit)? = null) {
        animJob?.cancel()
        val from = pos.floatValue
        animJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(durationMs, easing = easing)) { t, _ ->
                pos.floatValue = from + (target - from) * t
            }
            pos.floatValue = target
            onEnd?.invoke()
        }
    }

    fun snapBack(spring: Boolean) {
        animatePos(1f, if (spring) PAN_SETTLE_MS else PAN_MS, if (spring) PanSettleEase else PanEase)
    }

    /**
     * 收光：Web 的 `.pan-edge-glow` 写的是 `transition: opacity .34s var(--ease-out)`，
     * 松手时只把 `--pull` 归零、元素仍挂着 —— 于是「拉开 → 顶住 → 收光」是一条连续的衰减。
     * 这里若直接把 glowSide 置 0，合成节点会当场消失，观感是「闪断」。
     */
    fun fadeGlowOut() {
        if (glowSide == 0 && pull.floatValue <= 0f) return
        glowJob?.cancel()
        val from = pull.floatValue
        glowJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(GLOW_FADE_MS, easing = PanEase)) { t, _ ->
                pull.floatValue = from * (1f - t)
            }
            pull.floatValue = 0f
            glowSide = 0
        }
    }

    // 换章 / 重置：直接落位，不补动画
    LaunchedEffect(resetKey) {        if (lastReset == resetKey) return@LaunchedEffect
        lastReset = resetKey
        animJob?.cancel()
        pendingCommit = null
        glowSide = 0
        pull.floatValue = 0f
        shown = pageIndex
        pos.floatValue = 1f
    }

    // 页码变化统一在这里变成动画：点击 / 滑块 / 跨章。
    // 拖动提交不走这里 —— 轨道在回调里已经自己落位（见 onDragStopped）。
    LaunchedEffect(pageIndex) {
        if (pageIndex == shown) return@LaunchedEffect
        if (abs(pageIndex - shown) != 1) {
            animJob?.cancel()
            shown = pageIndex
            pos.floatValue = 1f
            return@LaunchedEffect
        }
        // 相邻页：先把轨道摆到**旧页**所在的槽位（这一帧不补动画），再滑到新页居中。
        // 换算见 [panEntrySlot] —— 起点取错会让整段动画反向，且中点露出没加载过的邻页。
        val side = panEntrySlot(from = shown, to = pageIndex, rtl = rtl).toFloat()
        animJob?.cancel()
        shown = pageIndex
        pos.floatValue = side
        animatePos(1f, PAN_MS, PanEase)
    }

    /**
     * 上一次拖动遗留的偏移：**必须归零**，否则「回弹到 1」之后若再来一个零位移的
     * delta，会把 pos 直接写回上次的终点 —— 于是下一次手势起手位置偏高，
     * 连拖几下就累积过 0.18 的提交阈值，轻碰也翻页。
     */
    val dragBase = remember { mutableFloatStateOf(1f) }
    val dragSum = remember { mutableFloatStateOf(0f) }
    val dragLive = remember { mutableStateOf(false) }

    // 捏合开始后整条翻页手势下线：内层 draggable 若还挂着，两指掰开时会被当成横向拖动。
    // 提交挂起期间不接受新的拖动（Web 的 onPointerDown 开头就 return），但**不能让
    // pendingCommit 参与 modifier 的构造** —— 那会让 draggable 节点在每次提交时被销毁重建，
    // 手势回调与本帧的组合不同代，pos 会读到上一次的残留。判断改到回调里做。
    val dragModifier = if (!gesturesEnabled) {
        Modifier
    } else {
        Modifier.draggable(
            state = rememberDraggableState { dx ->
                // 吸附动画还没提交完：这一下不接受，否则 pos 与动画互相覆盖
                if (pendingCommit != null) return@rememberDraggableState
                // 起手位置取「这一次手势开始时」的 pos。onDragStarted 正常会先把 dragLive
                // 置 false，但手势节点若被重建过、回调不同代，就靠这里兜底重取一次：
                // 宁可把多余的 delta 丢掉，也不能拿上一次的终点当起点。
                if (!dragLive.value) {
                    dragBase.floatValue = pos.floatValue
                    dragSum.floatValue = 0f
                    dragLive.value = true
                }
                dragSum.floatValue -= dx / widthPx.coerceAtLeast(1f)
                var next = dragBase.floatValue + dragSum.floatValue
                // 抵抗只在「这一侧真没东西了」时出现：章末还有下一章时要能顺畅拖出去
                when {
                    next < 1f && !canGoLeft -> {
                        next = 1f + (next - 1f) * PAN_RUBBER
                        glowSide = -1
                        pull.floatValue = min(1f, (1f - next) / EDGE_PULL_MAX)
                    }
                    next > 1f && !canGoRight -> {
                        next = 1f + (next - 1f) * PAN_RUBBER
                        glowSide = 1
                        pull.floatValue = min(1f, (next - 1f) / EDGE_PULL_MAX)
                    }
                    else -> {
                        glowSide = 0
                        pull.floatValue = 0f
                    }
                }
                pos.floatValue = next.coerceIn(-0.6f, 2.6f)
            },
            orientation = Orientation.Horizontal,
            onDragStarted = {
                if (pendingCommit != null) return@draggable
                dragBase.floatValue = pos.floatValue
                dragSum.floatValue = 0f
                dragLive.value = true
                animJob?.cancel()
                // 起手立刻抹掉上一次的收光，接下来由拖动逐帧写 --pull（Web 同款）
                glowJob?.cancel()
                glowSide = 0
                pull.floatValue = 0f
            },
            onDragStopped = { velocityPxPerSec ->
                // 窗口必须从**松手**开始算：放在 onDragStarted 的话，拖住超过 350ms
                // 再松手窗口已经过期，tap 照样漏进来。
                val dragged = dragLive.value
                dragLive.value = false
                if (dragged) PanDragGuard.arm()
                // 手势被缩放接管：这一下拖动不算翻页
                if (zoomActive()) {
                    fadeGlowOut()
                    snapBack(spring = false)
                    return@draggable
                }
                val vx = velocityPxPerSec / 1000f
                val moved = pos.floatValue - 1f
                val toward = when {
                    abs(vx) >= PAN_COMMIT_VELOCITY -> if (vx > 0f) -1 else 1
                    abs(moved) >= PAN_COMMIT_RATIO -> if (moved < 0f) -1 else 1
                    else -> 0
                }
                fadeGlowOut()
                if (toward == 0) {
                    snapBack(spring = false)
                    return@draggable
                }
                val target = if (toward == -1) slots[0] else slots[2]
                val canGo = if (toward == -1) canGoLeft else canGoRight
                val forward = if (toward == 1) !leftIsNext else leftIsNext
                if (target < 0 && !canGo) {
                    // 整本到头：直接抵住回弹，不先滑进空白槽位再回来
                    val pulled = min(1f, abs(pos.floatValue - 1f) / EDGE_PULL_MAX)
                    snapBack(spring = true)
                    if (pulled >= EDGE_TELL_RATIO && !onEdge(forward)) onBlocked(forward)
                    return@draggable
                }
                if (target < 0) {
                    // 这一侧没有相邻页但有相邻章：先滑出去，动画结束后再问外层。
                    // 换不了章（整部到头）就补一次整页回弹 —— 轻碰不打扰，但真到头要说清楚
                    animatePos(if (toward == -1) 0f else 2f, PAN_MS, PanEase) {
                        if (!onEdge(forward)) {
                            snapBack(spring = true)
                            onBlocked(forward)
                        }
                    }
                    return@draggable
                }
                pendingCommit = target
                animatePos(if (toward == -1) 0f else 2f, PAN_MS, PanEase) {
                    if (pendingCommit != null) {
                        pendingCommit = null
                        // 先把轨道落到新页再提页码：让 shown 和 pageIndex 的变化落在同一帧。
                        // 交给 LaunchedEffect(pageIndex) 去收尾会走「邻页入场」分支，
                        // 把旧页摆回中间再滑一遍 —— 观感就是整屏位移播两次。
                        shown = target
                        pos.floatValue = 1f
                        onCommit(target)
                    }
                }
            },
        )
    }

    // 拖动被缩放中途打断时，draggable 不会走 onDragStopped：轨道可能停在半页处，这里补回落位
    LaunchedEffect(gesturesEnabled) {
        if (!gesturesEnabled) {
            animJob?.cancel()
            glowSide = 0
            pull.floatValue = 0f
            if (abs(pos.floatValue - 1f) > 0.001f) animatePos(1f, PAN_MS, PanEase)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .then(dragModifier),
    ) {
        val w = constraints.maxWidth.toFloat()
        SideEffect { widthPx = w }

        // 三个槽位各自满屏叠放，只靠 translationX 平移：不用 Row 摆放，
        // 免得「子项比容器宽」时的排列行为把槽位挤成半屏（实测过）。
        slots.forEachIndexed { i, p ->
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // 拆分双页的画框比槽位宽（半页放大后 2 倍屏宽），不裁就会盖到邻槽上
                    .graphicsLayer {
                        translationX = (i - pos.floatValue) * w
                        clip = true
                    },
            ) {
                if (p >= 0) {
                    key(p) { slot(p) }
                } else {
                    // 章首/章末的空槽：黑底，拖出去时才看得到
                    Spacer(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }

        if (glowSide != 0) {
            EdgeGlow(
                side = glowSide,
                pull = { pull.floatValue },
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.26f).align(
                    if (glowSide < 0) Alignment.CenterStart else Alignment.CenterEnd,
                ),
            )
        }
    }
}

/** 边界柔光：压在拉开的那道缝上，亮度 = 实际拉开的比例。 */
@Composable
private fun EdgeGlow(side: Int, pull: () -> Float, modifier: Modifier) {
    val brand = LocalBrandColors.current
    // Web `.pan-edge-glow` 的底色是 brand-200/.85 → brand-400/.34（34%）→ 透明（94%），
    // 外面再乘一层横向 mask（1 → .46（34%）→ 0（82%））。两层复合后的等效值就是下面三个
    // 停靠点（0%: .85 / 34%: 0.34×0.46≈.156 / 82%: 0），按合成值一次画完，省掉 mask 层。
    val stops = arrayOf(
        0f to brand.brand200.copy(alpha = 0.85f),
        0.34f to brand.brand400.copy(alpha = 0.156f),
        0.82f to Color.Transparent,
    )
    Box(
        modifier.drawBehind {
            val p = pull()
            if (p <= 0f) return@drawBehind
            val brush = if (side < 0) {
                Brush.horizontalGradient(*stops, startX = 0f, endX = size.width)
            } else {
                Brush.horizontalGradient(*stops, startX = size.width, endX = 0f)
            }
            drawRect(brush = brush, topLeft = Offset.Zero, alpha = (p * 0.95f).coerceIn(0f, 1f))
        },
    )
}
