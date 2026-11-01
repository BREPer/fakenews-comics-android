package com.fakenews.comics.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fakenews.comics.data.session.FitMode
import com.fakenews.comics.data.session.PageAnimation
import com.fakenews.comics.ui.common.ImageOffGlyph
import com.fakenews.comics.ui.common.pressClickable
import kotlin.math.min
import kotlin.math.roundToInt

/* ================= 单图 / 失败态 ================= */

/** 页图加载完的淡入时长（Web `page-image.tsx` 的 `duration-150`） */
private const val PAGE_FADE_IN_MS = 150

/** 宽幅跨页图的判据：宽高比达到这个值才认为「一张图跨了两页」 */
private const val SPREAD_RATIO = 1.2f

/**
 * 页图失败态（逐值对齐 Web `page-image.tsx:169-195`，三个翻页模式共用同一块）。
 *
 * 之前只有一行「第 N 页没能显示」+ 一个 Material 实心按钮。缺的不只是观感：Web 里那句
 * 「图片可能已损坏或格式不支持，跳过这一页不影响后面的阅读」是这块界面**唯一**告诉用户
 * 「不用管它，继续往下看」的信息，少了它读者只能在一屏黑上干等。
 *
 * 版式：图标 22dp / white 35% → 主文案 12sp（text-xs）white 60% → 说明 11sp 行高 18sp
 * white 35% → 半透次级按钮（`bg-white/10` + `ring-white/15`，**不是**品牌色实心），
 * 间距 gap-2（8dp）、左右 px-6（24dp）、最矮 min-h-24（96dp）。
 */
@Composable
fun PageFailure(index: Int, onRetry: () -> Unit) {
    Column(
        // 用自身的 fillMaxSize 而不是调用方的 modifier：拆分双页给 PageImage 的 modifier
        // 是「按半页比例落位」的固定尺寸布局，拿来排版失败提示会把文字铺到两倍屏宽上。
        // 在卷轴（LazyColumn，高度无界）里 fillMaxSize 会退化成「只撑高度下限」，正是想要的。
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        ImageOffGlyph(size = 22.dp, color = Color.White.copy(alpha = 0.35f))
        Text(
            text = "第 ${index + 1} 页没能显示",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.60f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "图片可能已损坏或格式不支持，跳过这一页不影响后面的阅读",
            fontSize = 11.sp,
            lineHeight = 18.sp, // leading-relaxed ≈ 1.625
            color = Color.White.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "重新加载",
            modifier = Modifier
                .padding(top = 4.dp) // mt-1
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .pressClickable(pressedScale = 0.94f, onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 6.dp), // px-3 py-1.5
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.80f),
        )
    }
}

/**
 * 页图。`fit` 只对单页有四种取值；双页/拆分各自固定自己的适配方式（与 Web 端一致）。
 *
 * @param fadeIn 加载完是否淡入 150ms（Web 同名参数）。平移轨道的邻页必须关掉：轨道是
 *   「胶片」语义，相邻格子本来就该是实心的，淡入会让滑动过程中透出黑底。
 */
@Composable
fun PageImage(
    index: Int,
    url: String?,
    modifier: Modifier = Modifier,
    fit: FitMode = FitMode.CONTAIN,
    alignment: Alignment = Alignment.Center,
    contentScaleOverride: ContentScale? = null,
    fadeIn: Boolean = true,
    onLoaded: (IntSize) -> Unit = {},
) {
    if (url == null) {
        Spacer(modifier)
        return
    }
    val context = LocalContext.current
    var retry by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }

    if (failed) {
        PageFailure(index) {
            failed = false
            retry++
        }
        return
    }

    // 失败的响应同样进 HTTP 缓存，不换 URL 就永远拿回同一份错误
    val model = remember(url, retry, fadeIn) {
        ImageRequest.Builder(context)
            .data(if (retry == 0) url else "$url&retry=$retry")
            .crossfade(if (fadeIn) PAGE_FADE_IN_MS else 0)
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = "第 ${index + 1} 页",
        contentScale = contentScaleOverride ?: fit.toContentScale(),
        alignment = alignment,
        modifier = modifier,
        onSuccess = { success ->
            val size = success.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) {
                onLoaded(IntSize(size.width.roundToInt(), size.height.roundToInt()))
            }
        },
        onError = { failed = true },
    )
}

private fun FitMode.toContentScale(): ContentScale = when (this) {
    FitMode.WIDTH -> ContentScale.FillWidth
    FitMode.HEIGHT -> ContentScale.FillHeight
    FitMode.CONTAIN -> ContentScale.Fit
    FitMode.ORIGINAL -> ContentScale.None
}

/* ================= 点击分区（左三成 / 中间 / 右三成） ================= */

/**
 * @param turnEnabled 是否接受左右三成的翻页（卷轴模式只响应中间）
 * @param onTurn 屏幕方向的 ±1，阅读方向由 ViewModel 换算
 */
@Composable
fun TapZone(
    turnEnabled: Boolean,
    onTurn: (Int) -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    /*
     * key 里**只能放 turnEnabled**。
     *
     * 阅读器传进来的 onTurn / onTapCenter 是每次重组新建的 lambda（`ReaderScreen` 里
     * `val turn = { ... }` 没有 remember），把它们当 key 会让 pointerInput 每次重组
     * 重启协程 —— 按下与抬起之间只要发生过一次重组（图片加载完成、工具栏显隐、
     * 页码变化都算），这次点击就被取消，表现为「偶尔点不动」。
     * 用 rememberUpdatedState 取最新引用，协程只在 turnEnabled 真变化时重建。
     */
    val turn by rememberUpdatedState(onTurn)
    val tapCenter by rememberUpdatedState(onTapCenter)
    Box(
        modifier = modifier.pointerInput(turnEnabled) {
            detectTapGestures { offset ->
                // 拖动刚结束：这一下是尾巴，不是点击（Web 的 consumePanDragClick）
                if (PanDragGuard.consumeTail()) return@detectTapGestures
                when {
                    turnEnabled && offset.x < size.width * 0.3f -> turn(-1)
                    turnEnabled && offset.x > size.width * 0.7f -> turn(1)
                    else -> tapCenter()
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/* ================= 单页 ================= */

@Composable
fun SinglePage(
    imageIndex: Int,
    url: String?,
    fit: FitMode,
    turnEnabled: Boolean,
    onTurn: (Int) -> Unit,
    onTapCenter: () -> Unit,
    fadeIn: Boolean = true,
) {
    TapZone(turnEnabled, onTurn, onTapCenter, Modifier.fillMaxSize()) {
        if (fit == FitMode.ORIGINAL) {
            // 原始尺寸：容器可滚动，图片按自然像素铺开
            val h = rememberScrollState()
            val v = rememberScrollState()
            val density = LocalDensity.current
            var nat by remember(url) { mutableStateOf<IntSize?>(null) }
            Box(Modifier.fillMaxSize().horizontalScroll(h).verticalScroll(v)) {
                val size = nat
                PageImage(
                    index = imageIndex,
                    url = url,
                    fit = FitMode.ORIGINAL,
                    fadeIn = fadeIn,
                    modifier = if (size == null) {
                        Modifier.fillMaxSize()
                    } else {
                        with(density) { Modifier.requiredSize(size.width.toDp(), size.height.toDp()) }
                    },
                    onLoaded = { nat = it },
                )
            }
        } else {
            PageImage(
                index = imageIndex,
                url = url,
                fit = fit,
                fadeIn = fadeIn,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/* ================= 双页对开 ================= */

/**
 * 双页：两图并排，每页放进「半屏」格子再 contain 缩放。
 *
 * 不能按高度铺满（h-full w-auto）：手机上两页并排后的总宽远超视口，居中溢出会把两页
 * 各自的外侧裁掉近一半。contain 让每页都完整可见：屏幕够宽时自然铺满高度，窄屏上则是
 * 「整对缩小」。每页靠书脊一侧对齐，余量留给外侧。
 *
 * [spreadImages] 是对开里的图索引，由 `doubleSpreads` 决定 —— 首页/末页会各自单占一屏，
 * 所以不能在这里按 `spread * 2` 反推（那正是封面和内页挤成一屏的老 bug）。
 */
@Composable
fun DoubleSpread(
    spreadImages: List<Int>,
    rtl: Boolean,
    urlFor: (Int) -> String?,
    turnEnabled: Boolean,
    onTurn: (Int) -> Unit,
    onTapCenter: () -> Unit,
    fadeIn: Boolean = true,
) {
    // rtl（日漫右开）：先读右页，DOM 流从左到右，所以后一张要放在前面
    val ordered = if (rtl) spreadImages.reversed() else spreadImages

    TapZone(turnEnabled, onTurn, onTapCenter, Modifier.fillMaxSize()) {
        // gap-1 = 4dp；不留额外的横向 padding，两半格的宽度就是 (屏宽 - 4dp) / 2（与 Web 一致）
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ordered.forEachIndexed { i, img ->
                Box(
                    // 单占一屏的那张（章首页 / 章末页）：整屏给它。
                    // 旧行为是给它半屏格居中 —— 封面会缩成半屏宽居中，观感像「页面缩水了」，
                    // 也和 Web 的单页跨不一致。
                    modifier = if (ordered.size == 1) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.weight(1f).fillMaxHeight()
                    },
                    contentAlignment = when {
                        ordered.size == 1 -> Alignment.Center
                        i == 0 -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    },
                ) {
                    PageImage(
                        index = img,
                        url = urlFor(img),
                        fit = FitMode.CONTAIN,
                        fadeIn = fadeIn,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/* ================= 拆分双页 ================= */

private data class SplitGeom(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * 一张图在容器里的落位：宽幅图按「半页」适配，竖版图按整页适配（与 Web 端 splitGeom 同一套算式）。
 *
 * 不能拿「图比视口宽」当判据：手机视口又窄又高（390×844），按高度铺满后连普通竖版页都比
 * 视口宽，结果是每张竖版页都被当跨页拆开、左右各裁一截。
 */
private fun splitGeom(boxW: Float, boxH: Float, natW: Float, natH: Float, showRight: Boolean): SplitGeom {
    if (natW <= 0f || natH <= 0f) return SplitGeom(0f, 0f, boxW, boxH)
    val spread = natW / natH >= SPREAD_RATIO
    val scale = if (spread) {
        min(boxH / natH, boxW / (natW / 2f))
    } else {
        min(boxH / natH, boxW / natW)
    }
    val w = natW * scale
    val h = natH * scale
    val x = if (spread) (boxW - w / 2f) / 2f - (if (showRight) w / 2f else 0f) else (boxW - w) / 2f
    return SplitGeom(x = x, y = (boxH - h) / 2f, w = w, h = h)
}

/**
 * 拆分双页的一个「半页」：同一张图，只看左半或右半。
 *
 * 竖版页的两半是同一张整图（页码域 1 图 2 页的已接受取舍）；宽幅跨页图按半页缩放，
 * 通过整体水平位移换到另一半。
 */
@Composable
fun SplitPage(
    imageIndex: Int,
    showRight: Boolean,
    url: String?,
    animation: PageAnimation,
    turnEnabled: Boolean,
    onTurn: (Int) -> Unit,
    onTapCenter: () -> Unit,
) {
    TapZone(turnEnabled, onTurn, onTapCenter, Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            var nat by remember(url) { mutableStateOf<IntSize?>(null) }
            val geom = nat?.let { splitGeom(boxW, boxH, it.width.toFloat(), it.height.toFloat(), showRight) }

            // 两半之间是同一张图的平移：首次量到尺寸那帧不能过渡，否则首屏会自己横滑一下
            val shift = remember(imageIndex) { Animatable(0f) }
            var armed by remember(imageIndex) { mutableStateOf(false) }
            LaunchedEffect(geom?.x) {
                val target = geom?.x ?: return@LaunchedEffect
                if (!armed) {
                    armed = true
                    shift.snapTo(target)
                } else {
                    shift.animateTo(
                        target,
                        animationSpec = if (animation == PageAnimation.NONE) {
                            tween(durationMillis = 0)
                        } else {
                            tween(PAN_MS, easing = PanEase)
                        },
                    )
                }
            }

            Box(Modifier.fillMaxSize().clipToBounds()) {
                PageImage(
                    index = imageIndex,
                    url = url,
                    fit = if (geom == null) FitMode.CONTAIN else FitMode.ORIGINAL,
                    // 落位框已按半页比例算好：铺满它，而不是按原位图尺寸居中
                    contentScaleOverride = if (geom == null) null else ContentScale.FillBounds,
                    // Web 的 SplitLeaf 是不带 transition 的裸 <img>：拆分模式从不淡入
                    fadeIn = false,
                    modifier = if (geom == null) {
                        Modifier.fillMaxSize()
                    } else {
                        val g = geom
                        // 自己测量、自己落位：拆分图的画框比槽位宽（半页放大后 2 倍屏宽），
                        // 交给父级摆放会被「居中对齐」挪半页，两半就都露出一截。
                        Modifier.layout { measurable, _ ->
                            val placeable = measurable.measure(
                                Constraints.fixed(g.w.roundToInt(), g.h.roundToInt()),
                            )
                            val x = (if (armed) shift.value else g.x).roundToInt()
                            layout(boxW.roundToInt(), boxH.roundToInt()) {
                                placeable.place(x, g.y.roundToInt())
                            }
                        }
                    },
                    onLoaded = { nat = it },
                )
            }
        }
    }
}
