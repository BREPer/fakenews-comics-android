package com.fakenews.comics.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 骨架流光的缓动：Web `@keyframes skeleton-shimmer` 用的是
 * `cubic-bezier(0.4, 0, 0.2, 1)`（Material 的标准曲线），不是 linear。
 * 线性扫光是匀速机械感，缓入缓出才有「一道光扫过去」的观感 —— 这个差别
 * 在高光带宽只有 0.6 屏宽时特别明显。
 */
private val ShimmerEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * 骨架屏（对齐 Web .skeleton + skeleton-shimmer）：一条高光从左扫到右，
 * 比单纯闪烁更有质感，且自动跟随主题（底色 surfaceVariant、高光 outline）。
 * 与真实内容同构同尺寸，数据到达时无缝替换，不会把下面的内容顶下去。
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.outline
    val transition = rememberInfiniteTransition(label = "skeleton")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = ShimmerEase),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonShimmer",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(base)
                val w = size.width
                if (w <= 0f) return@drawBehind
                // 高光带从左外侧扫到右外侧：两端都在屏外，循环时不会有可见的跳变
                val band = w * 0.6f
                val start = -band + (w + band) * 2f * x
                drawRect(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            highlight.copy(alpha = 0.4f),
                            highlight.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                        startX = start,
                        endX = start + band,
                    ),
                )
            },
    )
}

/**
 * 书架网格骨架：封面 3:4 + 两行文字，与 ComicCard 同构。
 *
 * **故意不用 LazyVerticalGrid**：首页改成整页滚动后，骨架是滚动容器里的一个 item，
 * 高度约束是 `Infinity`；嵌套的 Lazy 容器拿到无限高度会直接崩（且同向嵌套本来就被禁）。
 * 骨架一共 12 格，普通 Column + Row 完全没有性能问题，换来的是「放在哪儿都能量出高度」。
 */
@Composable
fun ComicGridSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 12,
    /** 左右内距：调用方已经是带 padding 的滚动容器时传 0，否则会多出一份 */
    sidePadding: Dp = 16.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = sidePadding, end = sidePadding, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        count.coerceAtLeast(1).let { n ->
            val rows = (n + 2) / 3
            repeat(rows) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { c ->
                        val index = r * 3 + c
                        if (index < n) {
                            Column(Modifier.weight(1f)) {
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                // Web ComicGridSkeleton：`mt-2 h-3 w-3/4` + `mt-1.5 h-2.5 w-1/2`。
                                // 标题占 3/4、副标题占 1/2 是刻意的：全宽会让骨架看起来
                                // 比真实卡片「重」，数据到达时那两条灰块一缩，像抖了一下。
                                Spacer(Modifier.height(8.dp))
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth(0.75f).height(12.dp),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                                    shape = RoundedCornerShape(4.dp),
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 「继续阅读」横条骨架：标题 + 5 张同尺寸小卡（Web Home 同款占位） */
@Composable
fun ContinueRowSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 4.dp, bottom = 8.dp)) {
        SkeletonBox(
            // Web：`mb-2.5 h-[21px] w-24`
            modifier = Modifier.padding(horizontal = 16.dp).width(96.dp).height(21.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            items(5) {
                // 卡片宽度必须与真实卡片一致（ContinueCard 的 `w-28` = 112dp），
                // 否则骨架换成内容的那一刻整排会横向错一下
                Column(Modifier.width(112.dp)) {
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.75f).height(12.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                }
            }
        }
    }
}

/**
 * 详情页骨架（对齐 Web `pages/Detail.tsx:18-44` 的 `DetailSkeleton`）：
 * 手机断点是**上下堆叠**——160dp 封面居中、文字块（标题/元信息/大段简介/三个按钮）、
 * 章节标题 + 5 条章节行。
 *
 * 尺寸对齐的是 **Android 真实布局**（按钮 44dp、章节行 44dp），不是 Web 的 36/48：
 * 骨架与内容同高才不会在数据到达时把下面的东西顶一下。
 * 顶栏的返回键是真实存在的，所以这里没有 Web 那条 h-8 的返回条占位。
 */
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    ) {
        // 封面：`aspect-[3/4] w-40 self-center rounded-2xl`
        SkeletonBox(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(160.dp)
                .aspectRatio(3f / 4f),
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(16.dp)) // 封面 ↔ 文字 gap-4
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.66f).height(24.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.33f).height(12.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.80f).height(12.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.66f).height(12.dp))
            // 按钮行：`mt-3` 12dp + `gap-2` 8dp
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBox(Modifier.size(width = 112.dp, height = 44.dp), RoundedCornerShape(12.dp))
                SkeletonBox(Modifier.size(width = 96.dp, height = 44.dp), RoundedCornerShape(12.dp))
                SkeletonBox(Modifier.size(width = 96.dp, height = 44.dp), RoundedCornerShape(12.dp))
            }
        }
        Spacer(Modifier.height(20.dp)) // 文字 ↔ 章节 gap-5
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.width(96.dp).height(16.dp))
            repeat(5) {
                SkeletonBox(
                    Modifier.fillMaxWidth().height(44.dp),
                    RoundedCornerShape(12.dp),
                )
            }
        }
    }
}
