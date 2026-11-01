package com.fakenews.comics.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.fakenews.comics.data.session.FitMode
import com.fakenews.comics.data.session.PageAnimation
import com.fakenews.comics.data.session.ReaderMode
import com.fakenews.comics.data.session.ReaderSettings
import com.fakenews.comics.ui.common.bezierEasing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private const val LOCATE_TIMEOUT_MS = 3_000L

/** 水平滑动超过这个距离才当成翻页（Web 端 48px） */
private val SWIPE_SLOP = 48.dp

/* ================= 入场动画（轻移 / 淡入 / 无） ================= */

/** 260ms 的轻移：从翻页方向滑入 32px 并轻微放大；rtl 镜像，贴合日漫右翻的视觉流向 */
private val SlideEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** 淡入用的 CSS 关键字 `ease-out`（cubic-bezier(0,0,.58,1)），Web 的 `.anim-page-fade` 就是它 */
private val FadeEase = CubicBezierEasing(0f, 0f, 0.58f, 1f)

@Composable
private fun PageEntrance(
    animation: PageAnimation,
    forward: Boolean,
    rtl: Boolean,
    content: @Composable () -> Unit,
) {
    if (animation == PageAnimation.NONE || animation == PageAnimation.PAN) {
        content()
        return
    }
    // 方向/朝向只在挂载时取一次：动画途中因别的原因重组不该让位移反向
    val forward0 = remember { forward }
    val rtl0 = remember { rtl }
    val density = LocalDensity.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(
            1f,
            animationSpec = if (animation == PageAnimation.FADE) {
                tween(220, easing = FadeEase)
            } else {
                tween(260, easing = SlideEase)
            },
        )
    }
    val t = anim.value
    val dx = if (animation == PageAnimation.SLIDE) {
        with(density) { 32.dp.toPx() } * (if (forward0) 1f else -1f) * (if (rtl0) -1f else 1f)
    } else {
        0f
    }
    Box(
        Modifier.graphicsLayer {
            translationX = dx * (1f - t)
            val scale = if (animation == PageAnimation.SLIDE) 0.985f + 0.015f * t else 1f
            scaleX = scale
            scaleY = scale
            alpha = t
            transformOrigin = TransformOrigin.Center
        },
    ) { content() }
}

/**
 * 非平移模式：静止一页 + 横滑侦测。滑动与点击走同一条「索引增量」通道（与 Web 端 useSwipe 一致）。
 *
 * @param onSwipe 索引增量：手指向左 = +1（ltr）/ -1（rtl）
 */
@Composable
fun StaticStage(
    pageIndex: Int,
    animation: PageAnimation,
    rtl: Boolean,
    gesturesEnabled: Boolean,
    /** 这一侧还有没有下一页 / 下一章：没有就只回弹，不去改页码 */
    canGo: (delta: Int) -> Boolean,
    onSwipe: (Int) -> Unit,
    /** 越界且换不了章：播整页回弹（Web 的 `.anim-edge-bump-*`） */
    onBlocked: (forward: Boolean) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    val density = LocalDensity.current
    // 方向记录用普通数组：只服务于「挂载那一次」的入场动画，不该触发重组
    val nav = remember { intArrayOf(pageIndex) }
    val forward = if (pageIndex == nav[0]) true else (pageIndex > nav[0]).also { nav[0] = pageIndex }
    var dragAccum by remember { mutableStateOf(0f) }
    // onSwipe 不在 pointerInput 的 key 里（放进去会每次重组重启手势），
    // 但也不能直接闭包捕获 —— 那就永远用挂载时那个 lambda。取最新引用。
    val swipe by rememberUpdatedState(onSwipe)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pageIndex, rtl, gesturesEnabled) {
                if (!gesturesEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragAccum += amount
                        change.consume()
                    },
                    onDragEnd = {
                        val slop = with(density) { SWIPE_SLOP.toPx() }
                        if (abs(dragAccum) > slop) {
                            PanDragGuard.arm()
                            val delta = if (dragAccum < 0) {
                                if (rtl) -1 else 1
                            } else {
                                if (rtl) 1 else -1
                            }
                            // 到边了：整页顶一下再回来。以前直接调 onSwipe，外层发现换不了章
                            // 就什么也不做 —— 屏幕上一点反馈都没有，用户以为手势没生效。
                            if (canGo(delta)) swipe(delta) else onBlocked(delta > 0)
                        }
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f },
                )
            },
    ) {
        key(pageIndex) {
            PageEntrance(animation, forward, rtl) { content(pageIndex) }
        }
    }
}

/* ================= 到头整页回弹（两条手势路径共用） ================= */

/**
 * 「翻到边了」的整页回弹状态。
 *
 * 抽出来是因为有**两条**手势路径：平移轨道（PanTrack）与静止页（StaticStage）。
 * 早先这份状态内嵌在 PanTrack 里，于是「轻移 / 淡入 / 无」三种动画偏好下拖到章首章末
 * 只有轨道滑回、没有那一下「顶到纸边」的反馈 —— 而 Web 的 `.anim-edge-bump-*` 挂在
 * 包住 pager 的那一层，四种动画偏好共用。
 */
private class EdgeBumpState {
    var seq by mutableIntStateOf(0)
    var forward by mutableStateOf(false)
    val x = Animatable(0f)
    val scale = Animatable(1f)

    /** 连顶两次也要各播一次：布尔只能表达「刚弹过」，第二次就没有信号了 */
    fun play(forward: Boolean) {
        this.forward = forward
        seq++
    }
}

/** Web `edge-bump-*` 的 animation-timing-function（每一段之间都用这条） */
private val EdgeBumpEase = bezierEasing(0.22f, 0.9f, 0.3f, 1f)
private const val EDGE_BUMP_MS = 340
private const val EDGE_BUMP_PUSH_DP = 9
private const val EDGE_BUMP_OVER_DP = 3

@Composable
private fun rememberEdgeBump(): EdgeBumpState {
    val state = remember { EdgeBumpState() }
    val density = LocalDensity.current
    LaunchedEffect(state.seq) {
        if (state.seq == 0) return@LaunchedEffect
        val dir = if (state.forward) -1f else 1f
        val push = with(density) { EDGE_BUMP_PUSH_DP.dp.toPx() } * dir
        val over = with(density) { EDGE_BUMP_OVER_DP.dp.toPx() } * -dir
        state.x.snapTo(0f)
        state.scale.snapTo(1f)
        coroutineScope {
            launch {
                state.x.animateTo(
                    0f,
                    keyframes {
                        durationMillis = EDGE_BUMP_MS
                        0f at 0
                        push at 68 using EdgeBumpEase
                        over at 197 using EdgeBumpEase
                        0f at 340 using EdgeBumpEase
                    },
                )
            }
            launch {
                state.scale.animateTo(
                    1f,
                    keyframes {
                        durationMillis = EDGE_BUMP_MS
                        1f at 0
                        0.997f at 68 using EdgeBumpEase
                        1.001f at 197 using EdgeBumpEase
                        1f at 340 using EdgeBumpEase
                    },
                )
            }
        }
    }
    return state
}

/** 应用回弹位移。只读状态、在 draw 阶段生效，不触发布局。 */
private fun Modifier.edgeBumpLayer(state: EdgeBumpState): Modifier = graphicsLayer {
    translationX = state.x.value
    scaleX = state.scale.value
    scaleY = state.scale.value
}

/* ================= 双指缩放 ================= */

/** 缩放下限就是「适配屏幕」；再缩小没有意义，还会露出黑边 */
private const val MIN_ZOOM = 1f

/** 上限与 Web 端一致：按 1080 宽下发的页面放大 4 倍已经到头，再放大只是糊 */
private const val MAX_ZOOM = 4f

/** 松手时低于这个倍数就回落到适配尺寸（捏合过程中已经夹在 1 以上，只剩手法抖动的零头） */
private const val ZOOM_IDLE_EPS = 1.01f

/** 放大后单指拖动超过这个距离才算拖动，否则仍按点击处理 */
private const val ZOOM_PAN_SLOP = 6f

/**
 * 画布变换。三个数值只被 draw 阶段读取，捏合/平移每帧不触发重组；
 * [interactive] 是唯一给组合用的状态，只在「放大或正在捏」时翻转。
 */
internal class ZoomState {
    val scale = mutableFloatStateOf(1f)
    val offsetX = mutableFloatStateOf(0f)
    val offsetY = mutableFloatStateOf(0f)
    var interactive by mutableStateOf(false)

    fun reset() {
        scale.floatValue = 1f
        offsetX.floatValue = 0f
        offsetY.floatValue = 0f
        interactive = false
    }

    /** 位移夹在「缩放后的图仍盖满视口」的范围内，注意是相对画布中心的位移 */
    fun apply(scale: Float, x: Float, y: Float, boxW: Float, boxH: Float) {
        val mx = boxW * (scale - 1f) / 2f
        val my = boxH * (scale - 1f) / 2f
        this.scale.floatValue = scale
        offsetX.floatValue = x.coerceIn(-mx, mx)
        offsetY.floatValue = y.coerceIn(-my, my)
    }

    /** 窗口尺寸变了（旋转 / 分屏 / 折叠开合）：保留倍数，重新夹位移 */
    fun clamp(boxW: Float, boxH: Float) {
        val s = scale.floatValue
        if (s <= ZOOM_IDLE_EPS) {
            if (s != 1f || offsetX.floatValue != 0f || offsetY.floatValue != 0f) reset()
            return
        }
        apply(s, offsetX.floatValue, offsetY.floatValue, boxW, boxH)
    }
}

/**
 * 双指缩放层：变换只作用于画布（照片层），工具栏不跟着放大。
 *
 * 手势全走 Initial 阶段：第二根手指一落下就先接管并消费，内层翻页（draggable / 点击分区）
 * 随即被取消，不会出现「捏合的同时翻了一页」。放大状态下的单击不消费，照旧交给点击分区——
 * 那里 turnEnabled 已置 false，单击只会显隐工具栏（与 Web 端一致）。
 */
@Composable
internal fun ZoomCanvas(
    state: ZoomState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .then(
                if (enabled) {
                    Modifier.pointerInput(state) { detectZoomGestures(state) }
                } else {
                    Modifier
                },
            ),
    ) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        LaunchedEffect(boxW, boxH) { state.clamp(boxW, boxH) }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = state.scale.floatValue
                    scaleY = state.scale.floatValue
                    translationX = state.offsetX.floatValue
                    translationY = state.offsetY.floatValue
                    transformOrigin = TransformOrigin.Center
                },
        ) { content() }
    }
}

private class PinchAnchor(
    val baseScale: Float,
    val baseDist: Float,
    val focusX: Float,
    val focusY: Float,
    val baseX: Float,
    val baseY: Float,
)

private class PanAnchor(
    val startX: Float,
    val startY: Float,
    val baseX: Float,
    val baseY: Float,
)

private suspend fun PointerInputScope.detectZoomGestures(state: ZoomState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val boxW = size.width.toFloat()
        val boxH = size.height.toFloat()
        val center = androidx.compose.ui.geometry.Offset(boxW / 2f, boxH / 2f)
        var pinch: PinchAnchor? = null
        var pan: PanAnchor? = null
        // 本次触摸序列是否真的动过：没动过就还是单击，交给点击分区
        var active = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (pressed.size >= 2) {
                pan = null
                val a = pressed[0].position
                val b = pressed[1].position
                val dist = (a - b).getDistance().coerceAtLeast(1f)
                val anchor = pinch
                if (anchor == null) {
                    // 第二根手指落下就先接管，不等它真的掰开：这时翻页手势已经开始也不能让它提交
                    state.interactive = true
                    active = true
                    val focus = (a + b) / 2f - center
                    pinch = PinchAnchor(
                        baseScale = state.scale.floatValue,
                        baseDist = dist,
                        focusX = focus.x,
                        focusY = focus.y,
                        baseX = state.offsetX.floatValue,
                        baseY = state.offsetY.floatValue,
                    )
                } else {
                    val s = (anchor.baseScale * dist / anchor.baseDist).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    // 以两指中点为锚：t2 = t1 + (1 - s2/s1) * f
                    val k = 1f - s / anchor.baseScale
                    state.apply(s, anchor.baseX + anchor.focusX * k, anchor.baseY + anchor.focusY * k, boxW, boxH)
                }
                event.changes.forEach { it.consume() }
            } else {
                pinch = null
                val c = pressed[0]
                val s = state.scale.floatValue
                if (s > ZOOM_IDLE_EPS) {
                    val p = pan ?: PanAnchor(
                        startX = c.position.x,
                        startY = c.position.y,
                        baseX = state.offsetX.floatValue,
                        baseY = state.offsetY.floatValue,
                    ).also { pan = it }
                    val dx = c.position.x - p.startX
                    val dy = c.position.y - p.startY
                    if (abs(dx) > ZOOM_PAN_SLOP || abs(dy) > ZOOM_PAN_SLOP) active = true
                    state.apply(s, p.baseX + dx, p.baseY + dy, boxW, boxH)
                    // 拖动要消费：否则同一次触摸会在松手时被当成单击，把工具栏切出来
                    if (active) c.consume()
                }
            }
        }

        if (state.scale.floatValue <= ZOOM_IDLE_EPS) {
            state.reset()
        } else {
            state.apply(state.scale.floatValue, state.offsetX.floatValue, state.offsetY.floatValue, boxW, boxH)
            state.interactive = true
        }
    }
}

/* ================= 模式舞台：挑平移轨道还是静止页 ================= */

/**
 * 单页 / 双页 / 拆分的统一入口：平移偏好走三槽轨道，其余偏好走「静止页 + 横滑」。
 *
 * @param pageIndex 模式页索引
 * @param imageCount 本章图数
 */
@Composable
fun ModeStage(
    mode: ReaderMode,
    pageIndex: Int,
    imageCount: Int,
    settings: ReaderSettings,
    urlFor: (Int) -> String?,
    onPage: (Int) -> Unit,
    onEdge: (forward: Boolean) -> Boolean,
    onTurn: (Int) -> Unit,
    onTapCenter: () -> Unit,
    resetKey: Any,
    canEdgePrev: Boolean,
    canEdgeNext: Boolean,
) {
    val total = modePageCount(mode, imageCount)
    // 换页 / 换章 / 关开关都复位缩放：新页不该继承上一页的倍数和位移
    val zoom = remember(settings.pinchZoom, resetKey, pageIndex) { ZoomState() }

    /**
     * 「原始」适配下容器本身就是可横向滚动的（`SinglePage` 给 ORIGINAL 挂了
     * horizontalScroll），再叠一条横拖轨道，两根手势会互抢。Web 的 SinglePager 在这里
     * 显式关掉平移（`panOn = animation === 'pan' && !isOriginal`），照同一条规则来。
     */
    val panOn = settings.animation == PageAnimation.PAN &&
        !(mode == ReaderMode.SINGLE && settings.fit == FitMode.ORIGINAL)

    /*
     * 到头整页回弹统一放在**这一层**：位移要作用于「包住 pager 的那块」，
     * 而且平移轨道与静止页两条手势路径都要有（Web 的 `.anim-edge-bump-*` 同样挂在外层，
     * 四种翻页动画共用）。之前只做在 PanTrack 内部，于是轻移/淡入/无三种偏好下
     * 拖到章首章末只是「滑回去」，没有任何「顶到纸边」的反馈。
     */
    val bump = rememberEdgeBump()

    /** 这一侧还有没有下一页 / 下一章。没有就只回弹，不改页码。 */
    val canGo: (Int) -> Boolean = { delta ->
        val target = pageIndex + delta
        target in 0 until total || (if (delta > 0) canEdgeNext else canEdgePrev)
    }

    /**
     * 只回弹、不重复通知：PanTrack 在调 [onBlocked] 之前**已经**问过 [onEdge]（换章失败
     * 才走到这里），提示由那边发出去，这里再发一次会变成两条。
     */
    val bumpOnly: (Boolean) -> Unit = { bump.play(it) }

    /** 回弹 + 通知：静止页 / 点击翻页越界时，外层还不知道「到头了」，得由这里说一声。 */
    val bumpAndTell: (Boolean) -> Unit = { forward ->
        bump.play(forward)
        onEdge(forward) // 返回 false = 确实换不了章；副作用是发出「已经是最后一页了」
    }

    /**
     * 点击翻页也走这个：到头时播回弹而不是「点了没反应」。
     * Web `ReaderShell.turn()` 的 else 分支同样是 `flashBump(dir)`。
     */
    val turnWithBump: (Int) -> Unit = { delta ->
        if (canGo(delta)) onTurn(delta) else bumpAndTell(delta > 0)
    }

    Box(Modifier.fillMaxSize().edgeBumpLayer(bump)) {
    ZoomCanvas(state = zoom, enabled = settings.pinchZoom) {
        if (panOn) {
            PanTrack(
                pageIndex = pageIndex,
                count = total,
                rtl = settings.rtl,
                resetKey = resetKey,
                canEdgePrev = canEdgePrev,
                canEdgeNext = canEdgeNext,
                gesturesEnabled = !zoom.interactive,
                zoomActive = { zoom.interactive },
                onCommit = onPage,
                onEdge = onEdge,
                onBlocked = bumpOnly,
            ) { page ->
                ModePageContent(
                    mode, page, imageCount, settings, urlFor,
                    turnEnabled = !zoom.interactive,
                    onTurn = turnWithBump,
                    onTapCenter = onTapCenter,
                    // 轨道是胶片语义：相邻格子在滑进来之前就该是实心的，淡入会透出黑底
                    fadeIn = false,
                )
            }
        } else {
            StaticStage(
                pageIndex = pageIndex,
                // 平移被关掉时退回「无动画」：Web 的 pageAnim('pan') 返回的正是空类名。
                // 不能退回轻移 —— 那会在原始尺寸的可滚动容器里再叠一次横向位移。
                animation = if (settings.animation == PageAnimation.PAN) {
                    PageAnimation.NONE
                } else {
                    settings.animation
                },
                rtl = settings.rtl,
                gesturesEnabled = !zoom.interactive,
                canGo = canGo,
                onSwipe = { delta ->
                    val target = pageIndex + delta
                    when {
                        target in 0 until total -> onPage(target)
                        else -> onEdge(delta > 0)
                    }
                },
                onBlocked = bumpAndTell,
            ) { page ->
                ModePageContent(
                    mode, page, imageCount, settings, urlFor,
                    turnEnabled = !zoom.interactive,
                    onTurn = turnWithBump,
                    onTapCenter = onTapCenter,
                    // 静止页就是「新页挂载」本身，与 Web 一样淡入 150ms
                    fadeIn = true,
                )
            }
        }
    }
    }
}

/** 一个模式页里画什么：单图 / 对开 / 半页。 */
@Composable
private fun ModePageContent(
    mode: ReaderMode,
    page: Int,
    imageCount: Int,
    settings: ReaderSettings,
    urlFor: (Int) -> String?,
    turnEnabled: Boolean,
    onTurn: (Int) -> Unit,
    onTapCenter: () -> Unit,
    fadeIn: Boolean,
) {
    when (mode) {
        ReaderMode.SINGLE -> SinglePage(
            imageIndex = page,
            url = urlFor(page),
            fit = settings.fit,
            turnEnabled = turnEnabled,
            onTurn = onTurn,
            onTapCenter = onTapCenter,
            fadeIn = fadeIn,
        )
        ReaderMode.DOUBLE -> DoubleSpread(
            // 对开的图索引由 doubleSpreads 决定（首页/末页单占），不能在这里按 page * 2 反推
            spreadImages = doubleSpreads(imageCount).getOrElse(page) { emptyList() },
            rtl = settings.rtl,
            urlFor = urlFor,
            turnEnabled = turnEnabled,
            onTurn = onTurn,
            onTapCenter = onTapCenter,
            fadeIn = fadeIn,
        )
        else -> SplitPage(
            imageIndex = page / 2,
            showRight = if (settings.rtl) page % 2 == 0 else page % 2 == 1,
            url = urlFor(page / 2),
            animation = settings.animation,
            turnEnabled = turnEnabled,
            onTurn = onTurn,
            onTapCenter = onTapCenter,
        )
    }
}

/* ================= 卷轴 ================= */

/**
 * 条漫：竖向连续滚动，整幅按屏宽铺开；页码由视口顶端的条目反推。
 *
 * 首次定位要等图片量出高度：LazyColumn 在测量前会把 scrollToItem 夹回 0，
 * 落位前列表反馈也不作数（否则续读会被顶回第一页）。
 */
@Composable
internal fun WebtoonPager(
    pageIndex: Int,
    pageCount: Int,
    urlFor: (Int) -> String?,
    onSettle: (Int) -> Unit,
    onTapCenter: () -> Unit,
    pinchZoom: Boolean,
    /*
     * 缩放状态默认自己持有；测试需要读到缩放值，可以直接传一个进来。
     * 章节 / 模式的切换由 ReaderScreen 外层 `key()` 整块重建处理，
     * 所以这里的 remember 只需要跟开关走 —— **不能跟 pageIndex**，
     * 滚动时它一直在变，等于边滚边把缩放清掉。
     */
    zoomState: ZoomState = remember(pinchZoom) { ZoomState() },
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = pageIndex)
    var located by remember { mutableStateOf(false) }

    // Web 端把卷轴的缩放关掉了（`usePinchZoom(prefs.pinchZoom && mode !== 'webtoon')`），
    // Android 这里放开：条漫的长图经常需要看清细节，放大后的拖动由缩放层代管。
    val zoom = zoomState

    /*
     * 放大状态下 LazyColumn 收不到拖动（手势在 Initial 阶段就被缩放层吃掉），
     * 但**已经在跑的惯性不会自己停** —— 捏合开始的瞬间补一刀把它掐断，
     * 否则会出现「一边被放大、一边还在往下滑」。
     */
    LaunchedEffect(zoom.interactive) {
        if (zoom.interactive) listState.scroll { }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to located }
            .filter { it.second }
            .map { it.first }
            .distinctUntilChanged()
            .collect { onSettle(it) }
    }

    LaunchedEffect(pageIndex) {
        if (located) {
            if (listState.firstVisibleItemIndex != pageIndex) listState.scrollToItem(pageIndex)
            return@LaunchedEffect
        }
        withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.any { it.index == pageIndex && it.size > 0 }
            }.filter { it }.first()
        }
        listState.scrollToItem(pageIndex)
        located = true
    }

    /*
     * TapZone 必须包在 **LazyColumn 外面**，不能逐 item 套。
     *
     * 逐 item 套的时候 tap 检测在滚动容器内层，而纵向滚动在外层 —— Main pass 叶→根，
     * 松手那一帧内层先跑，滚动没能拦住的 UP 会被当成一次点击：**每滚一下都可能把
     * 工具栏切出来**。和横向拖动漏 tap 是同一个病。
     *
     * 提到外层就与 Web 同构（`<div onClick>` 包住滚动容器）：滚动时内层 scrollable
     * 消费 move，外层的 tap 自然取消。顺带把「N 个 item 各挂一个 pointerInput」
     * 收敛成一个。
     */
    /*
     * 缩放层包在 TapZone 外面：手势在 Initial 阶段先到这里，放大时把拖动整段吃掉，
     * 内层 LazyColumn 与 TapZone 都收不到，于是「放大后单指拖 = 平移画面」而不是
     * 「滚列表 + 顺手切出工具栏」。缩回 1 倍后一切照旧。
     */
    ZoomCanvas(state = zoom, enabled = pinchZoom) {
        TapZone(false, onTurn = {}, onTapCenter = onTapCenter, modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(count = pageCount) { index ->
                    PageImage(
                        index = index,
                        url = urlFor(index),
                        fit = FitMode.WIDTH,
                        // Web 卷轴的 <img> 没有 opacity 过渡：条漫边滚边加载，淡入只会变成闪烁
                        fadeIn = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 尾巴留白 = Web 的 `h-16`（64px）：末页下方还能再多滚一小段，
                // 让「最后一页」不会贴死在屏幕底边
                item { Spacer(Modifier.height(64.dp)) }
            }
        }
    }
}
