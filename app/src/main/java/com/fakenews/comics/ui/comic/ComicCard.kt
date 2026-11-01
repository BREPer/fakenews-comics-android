package com.fakenews.comics.ui.comic

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fakenews.comics.data.model.ComicDto
import com.fakenews.comics.data.model.isFinished
import com.fakenews.comics.data.model.subtitle
import com.fakenews.comics.ui.common.CardEntrance
import com.fakenews.comics.ui.common.EaseOutQuint
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 角标压暗：两个不同值，别混用 —— Web 里「已读完」是 `bg-black/55`、
 * 收藏圆徽是 `bg-black/45`（浅色封面上前者字小、需要更实的底）。
 */
private val TextBadgeScrim = Color.Black.copy(alpha = 0.55f)
private val HeartBadgeScrim = Color.Black.copy(alpha = 0.45f)

/** 圆徽/文字角标共用的一圈白色细描边：Web `ring-1 ring-white/10` */
private val BadgeRing = Color.White.copy(alpha = 0.10f)

/** 收藏徽标：胭脂红（固定语义色），不随高亮色变化 —— 与 Web --fav-500 同值 */
private val FavRed = Color(0xFFF03F24)

/**
 * 封面框：固定 3:4（与 Web 网格/骨架同比例，避免加载完成后整片网格跳动）。
 *
 * 逐值对齐 Web `ComicCover`（`components/comic/ComicCard.tsx:21-25`）：
 * `rounded-xl` 12dp、`bg-ink-800` 打底、`border 1px var(--card-border)` 细边、
 * `shadow-soft`（浅色黑 8%/20% 双层，深色黑 40%/50%）、加载淡入 300ms。
 *
 * 以前边框用的是 `outlineVariant`（浅色 #E4E8F0）—— 那个值和 ink-800 同色，
 * 等于「底色上再画一道同色边」，在浅色主题里完全看不见；Web 的 cardBorder 是
 * 黑 9%，它才是让封面从页面底色里「浮」出来的那一圈。
 */
@Composable
fun ComicCover(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    crossfade: Boolean = true,
    elevated: Boolean = true,
    /** 网格卡片是 `rounded-xl`（12dp），详情页大封面是 `rounded-2xl`（16dp） */
    shape: Shape = RoundedCornerShape(12.dp),
    /**
     * 叠在封面**裁切层内部**的内容（网格的阅读进度条）。
     *
     * 不能放在封面外面再套一层 clip 呀：外层一裁，封面的阴影就整片被切掉了
     * （实测开/关阴影的截图在网格区最大只差 2/765，等于看不见）。
     */
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val ink = LocalInk.current
    val context = LocalContext.current
    // url 变了要重置失败态（同一张卡先失败、后来换了签名参数能成功的情形）
    var failed by remember(url) { mutableStateOf(false) }
    // ImageRequest 必须 remember：它是 Coil 明确要求「不要每次重组都新建」的对象，
    // 每次都 Builder().build() 会让 AsyncImage 认为请求换了、重新走一遍相等性比较与
    // 内存缓存查找。网格滚动时几十张卡同时重组，这个分配开销会实打实地掉帧。
    val model = remember(url, crossfade, context) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(if (crossfade) 300 else 0)
            .build()
    }
    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .then(
                if (elevated) {
                    // shadow-soft 的近似：环境光 8% + 投射光 20%，半径对齐 14px 的扩散
                    Modifier.shadow(
                        elevation = 6.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.20f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(ink.ink800)
            .border(1.dp, LocalBrandColors.current.cardBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            BrokenImageGlyph()
        } else {
            AsyncImage(
                model = model,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        overlay()
    }
}

/**
 * 「图裂了」占位（Web 用 lucide 的 `ImageOff`，`size={20}`、`text-ink-600`）。
 * 核心图标集里没有 ImageOff，为一个图标引入 material-icons-extended 不值当，直接画：
 * 圆角矩形描边 + 一条斜杠，语义与 ImageOff 一致。
 *
 * 颜色是 ink-600（浅色 #8C94A2 / 深色 #515A72），不是 ink-300 —— 它是「缺图」的
 * 弱提示，不该和正文抢注意力。
 */
@Composable
private fun BrokenImageGlyph(size: Dp = 20.dp) {
    val color = LocalInk.current.ink600
    Canvas(Modifier.size(size)) {
        val stroke = 1.5.dp.toPx()
        val inset = stroke
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.22f, this.size.height * 0.78f),
            end = Offset(this.size.width * 0.78f, this.size.height * 0.22f),
            strokeWidth = stroke,
        )
    }
}

/**
 * 封面卡片（对齐 Web `ComicCard`）：
 * - 外层入场（anim-card-in，按序号错峰）；内层按压回弹 —— 两套 transform 分居两层，
 *   与 Web 同一理由（动画 fill 会盖住按压的 transform）。
 * - 标题 `text-xs font-medium text-ink-200`、`mt-2`（8dp）；副标题 `text-[11px] text-ink-500`。
 *   以前标题走 `onSurface`（ink-100）—— 比 Web 深一整档，卡片标题和页面标题一样黑，
 *   整片网格看不出层次。
 * - 进度条：brand-600 → brand-400 渐变 + 右端圆头 + 品牌色微光 + 宽度 500ms 过渡。
 */
@Composable
fun ComicCard(
    comic: ComicDto,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
) {
    val ink = LocalInk.current
    CardEntrance(index) {
        Column(
            modifier = modifier.pressClickable(onClick = onClick),
        ) {
            Box {
                // 进度条走 ComicCover 的 overlay 槽，铺在封面自己的裁切层里：
                // Web 的进度条是封面（overflow-hidden rounded-xl）的子节点，发光被圆角切掉；
                // 以前在外面再包一层 clip 来切，代价是连封面的阴影一起切没了。
                ComicCover(
                    coverUrl,
                    comic.title,
                    Modifier.fillMaxWidth(),
                ) {
                    val pct = comic.progress?.percent ?: 0.0
                    if (pct > 0) {
                        ProgressBar(
                            pct.toFloat().coerceIn(0f, 100f),
                            Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
                if (comic.isFinished) {
                    // Web: left-1.5 top-1.5 → rounded-md(6dp) → px-1.5 py-0.5 → text-[10px] medium
                    //      + ring-1 ring-white/10
                    Text(
                        "已读完",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(TextBadgeScrim, RoundedCornerShape(6.dp))
                            .border(1.dp, BadgeRing, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (comic.isFavorite) {
                    // Web: p-1（4dp 内距）+ ring-1 ring-white/10，心形 12px 实心胭脂红
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(HeartBadgeScrim, CircleShape)
                            .border(1.dp, BadgeRing, CircleShape)
                            .padding(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "已收藏",
                            tint = FavRed,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp)) // Web mt-2
            Text(
                comic.title,
                fontSize = 12.sp, // Web text-xs
                fontWeight = FontWeight.Medium,
                color = ink.ink200,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                comic.subtitle(),
                fontSize = 11.sp, // Web text-[11px]
                color = ink.ink500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 阅读进度条：黑底轨 + brand-600 → brand-400 渐变填充。
 *
 * 三个细节都照 Web（`ComicCard.tsx:48-53`）：
 *   - 右端 `rounded-r-full`（进度尖端是圆的，不是刀切）；
 *   - `shadow-[0_0_8px_rgb(brand500/0.5)]` 微光 —— Compose 没有廉价的 box-shadow，
 *     用一条竖向渐变的柔光条近似（中心最亮、上下 4dp 衰减到透明），比真模糊便宜
 *     且不会在低端 GPU 上失效；
 *   - 宽度 500ms `ease-out-quint` 过渡。
 */
@Composable
fun ProgressBar(percent: Float, modifier: Modifier = Modifier) {
    val brand = LocalBrandColors.current
    val fraction by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = EaseOutQuint),
        label = "progressWidth",
    )
    ProgressBarBody(fraction, modifier)
}

/** 即时版进度条（横滑列表/记录行复用）：滚动中不补间动画，避免抢帧 */
@Composable
fun ProgressBarStatic(percent: Float, modifier: Modifier = Modifier) {
    ProgressBarBody((percent / 100f).coerceIn(0f, 1f), modifier)
}

@Composable
private fun ProgressBarBody(fraction: Float, modifier: Modifier = Modifier) {
    val brand = LocalBrandColors.current
    val endShape = RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        if (fraction <= 0f) return@Box
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .drawBehind {
                    // 微光：以进度条为中心、上下各扩 4dp 的竖向渐变（模拟 8px blur 的衰减）
                    val glow = 4.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to brand.brand500.copy(alpha = 0.5f),
                            1f to Color.Transparent,
                            startY = -glow,
                            endY = size.height + glow,
                        ),
                        topLeft = Offset(0f, -glow),
                        size = Size(size.width, size.height + glow * 2),
                    )
                }
                .clip(endShape)
                .background(Brush.horizontalGradient(listOf(brand.brand600, brand.brand400))),
        )
    }
}
