package com.fakenews.comics.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.data.session.Accent
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.LocalIsDark

/**
 * 主按钮（对齐 Web .btn-primary）：上浅下深的竖向渐变 + 顶部 1px 白内高光 + shadow-soft + 白字，
 * 三件套叠起来才像「实体按键」；只画渐变会是一块贴在屏幕上的色块。
 * 按压回弹走 [pressClickable]（scale 0.97 / 150ms，即 Web `.btn` 的口径）；禁用压到 45% 透明度。
 *
 * 尺寸：Web 的 `.btn` 是 `px-3 py-2 text-sm` = 12px 横内距 / 36px 视觉高，靠 `.touch-hit`
 * 把**命中区**补到 44×44（视觉尺寸不变）。Android 没有伪元素，直接让按钮本体 44dp 高、
 * 横内距取 Web 的 12dp —— 视觉略厚一点，但命中区是实的，不靠透明扩展。
 */
@Composable
fun BrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    /** 文字右侧的尾巴（Web 的「去挑一本 <ChevronRight size={14}/>」这种） */
    trailing: (@Composable () -> Unit)? = null,
) {
    val brand = LocalBrandColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .pressClickable(
                enabled = enabled,
                pressedScale = 0.97f,
                durationMillis = 150,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.45f)
            // Web：`box-shadow: var(--shadow-soft), inset 0 1px 0 white/22`
            .shadow(
                elevation = 4.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .background(
                brush = Brush.verticalGradient(listOf(brand.gradFrom, brand.gradTo)),
                shape = shape,
            )
            // 顶部内高光必须裁进圆角。drawLine 是从 (0,0.5dp) 画到 (width,0.5dp) 的直
            // 线，而 12dp 圆角在 y≈0.5dp 处已经向内收了 8dp 多 —— 不裁的话这条线会在
            // 两个上角各伸出去一截。浅色面板上它和白底混在一起看不出来，深色面板上就是
            // 两条明晃晃的白刺（Web 的 `inset 0 1px 0` 天然跟着 border-radius 走，没这问题）。
            .clip(shape)
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.22f),
                    start = Offset(0f, 0.5.dp.toPx()),
                    end = Offset(size.width, 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp)) // Web：`.btn` 的 gap-1.5 = 6px
            trailing()
        }
    }
}

/**
 * 次级按钮（对齐 Web `.btn-ghost ring-1 ring-ink-800`）：**无填充**，只有一圈 ink-800
 * 细描边 + ink-300 文字。详情页的「收藏 / 编辑信息 / 重扫 / 重置进度」都是这一种。
 *
 * 之前用的是 Material 的 `OutlinedButton` —— 它自带 primary 色文字与 8dp 圆角，
 * 一排四个按钮会渲成「四个一样重的动作」，而 Web 里这些是明显的次级操作。
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    /**
     * 小号文字：Web 里设置页的清理/导出/选择文件/修改/退出与漫画库的「立即扫描此库」
     * 都是 `btn-ghost … text-xs`（12px），而详情页那排动作是默认的 14px。16 处调用里
     * 只有这几处需要小号，所以用开关而不是再拆一个组件。
     */
    small: Boolean = false,
    /**
     * 自定义图标槽：自绘字形、转圈、或带入场动画的图标（收藏的「弹一下」）。
     * 给了就不看 [icon] —— 那里只放得下静态 ImageVector。
     */
    iconContent: (@Composable () -> Unit)? = null,
) {
    val ink = LocalInk.current
    Row(
        modifier = modifier
            .height(44.dp)
            .pressClickable(
                enabled = enabled,
                pressedScale = 0.97f,
                durationMillis = 150,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.45f)
            .border(1.dp, ink.ink800, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when {
            iconContent != null -> {
                iconContent()
                Spacer(Modifier.width(6.dp))
            }
            icon != null -> {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (iconTint == Color.Unspecified) ink.ink300 else iconTint,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(
            text,
            color = ink.ink300,
            fontSize = if (small) 12.sp else 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 筛选 chip（对齐 Web .chip-on / .chip-off）：
 * 选中 = 品牌渐变底 + 白字；未选中 = 卡片底 + 一圈细描边。高 34dp（触屏主交互区，
 * Web 在 pointer:coarse 下同样放到 34）。按压 scale 0.96。
 */
@Composable
fun BrandChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val brand = LocalBrandColors.current
    val ink = LocalInk.current
    /*
     * Web 的 `.chip` 带 `transition-colors`，选中态是**渐变**过去的；这里原来是
     * `if (selected)` 直接换一整套绘制，点一下底色从白跳成品牌色。在筛选条这种
     * 「连点几个 chip 比结果」的场景里，硬跳会让整条看起来在闪。
     *
     * 用一个 0→1 的进度同时驱动底色、柔光、内高光、描边和文字色，五层共用同一条
     * 曲线，避免出现「底到了、字还没到」的错拍。进度在 draw 里读（`sel.value`），
     * 只重绘不重组；只有文字色必须在组合阶段读，代价是点击后这 160ms 里这个 chip
     * 会重组 —— 一次只点一个，可以忽略。
     */
    val sel = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(160, easing = EaseOutQuint),
        label = "chipSelected",
    )
    Row(
        modifier = modifier
            .height(34.dp)
            .pressClickable(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .drawBehind {
                val t = sel.value
                val radius = CornerRadius(size.height / 2f)
                if (t > 0.001f) {
                    // 底部品牌色柔光（Web `0 2px 10px -4px brand500/55`）。
                    // 压在底色下面：它是「从元素底边溢出的一点光」，不是面板的一部分。
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to brand.brand500.copy(alpha = 0.55f * t),
                            startY = -6.dp.toPx(),
                            endY = size.height + 6.dp.toPx(),
                        ),
                        cornerRadius = radius,
                    )
                }
                // 未选中底：ink-900（Web `.chip-off`）
                drawRoundRect(color = ink.ink900, cornerRadius = radius)
                if (t > 0.001f) {
                    // 选中底：brand-500 → gradTo 的竖向渐变（Web `.chip-on`）
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(brand.brand500, brand.gradTo)),
                        cornerRadius = radius,
                        alpha = t,
                    )
                    // 顶部 1px 内高光 `inset 0 1px 0 white/20`
                    //
                    // 必须画成**圆角**矩形。用 drawRect 的话，这层白雾会连药丸两端圆角
                    // 外侧一起涂上 —— 那里本该是透明背景，深色底上就是两端各一块发白的
                    // 月牙（浅色底和白底混在一起看不出来，所以这个 bug 只在深色下现形）。
                    // Web 的 inset box-shadow 天然沿 border-radius 走，没这问题。
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.20f * t),
                            1f to Color.Transparent,
                            startY = 0f,
                            endY = size.height * 0.45f,
                        ),
                        cornerRadius = radius,
                    )
                }
                // 未选中那圈 `ring-1 ring-inset ink-800/60`（选中时淡出，不做成硬切）
                if (t < 0.999f) {
                    drawRoundRect(
                        color = ink.ink800.copy(alpha = 0.60f * (1f - t)),
                        cornerRadius = radius,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            // 未选中是 ink-300（不是 onSurfaceVariant 的 ink-400）—— Web 原值
            color = lerp(ink.ink300, Color.White, sel.value),
        )
    }
}

/**
 * 卡片材质（对齐 Web `.card`，`index.css:322`）：
 * `rounded-2xl`(16dp) + `bg-ink-900` + `border 1px var(--card-border)`(浅色黑 9% / 深色白 7%)
 * + `shadow-soft`(黑 8%/20%) + 顶部 `inset 0 1px 0 var(--edge-light)` 内高光。
 *
 * 之前用的是 `outlineVariant` 描边 —— 那是 ink-800，和卡片底下的页面底色只差一档，
 * 浅色主题里等于「白卡片 + 一层浅灰线」，看得见边框却看不出层次；Web 的 cardBorder
 * 是 9% 黑，加上外阴影才是「浮起来的纸」。
 *
 * 顶部那道内高光只在深色下有意义（浅色卡片本身就是纯白），但两边都画 ——
 * 口径统一，值按主题取（Web 同一份 CSS 变量做同样的事）。
 */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val brand = LocalBrandColors.current
    val ink = LocalInk.current
    val edgeLight = if (LocalIsDark.current) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.White.copy(alpha = 0.90f)
    }
    Column(
        modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.20f),
            )
            .clip(shape)
            .background(ink.ink900)
            .drawWithContent {
                drawContent()
                drawLine(
                    color = edgeLight,
                    start = Offset(0f, 0.5.dp.toPx()),
                    end = Offset(size.width, 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .border(1.dp, brand.cardBorder, shape),
        content = content,
    )
}

/**
 * 开关（对齐 Web `settings-controls.tsx` 的 `Toggle`）：
 * 轨道 `h-6 w-11`（24×44dp）、滑块 20dp、开 `bg-brand-600` / 关 `bg-ink-700`，
 * 位移 20dp 走 transform、200ms `ease-out-quint`。
 *
 * 不用 Material 的 `Switch`：它是 52×32、开态用 primary + 描边滑块，和 Web 这颗
 * 无边框、纯色轨道的小开关完全不是一个观感（并排看会以为是两代产品）。
 */
@Composable
fun BrandSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val brand = LocalBrandColors.current
    val ink = LocalInk.current
    val trackW = 44.dp
    val knob = 20.dp
    val offset by animateDpAsState(
        targetValue = if (checked) trackW - knob - 4.dp else 2.dp,
        animationSpec = tween(200, easing = EaseOutQuint),
        label = "switchKnob",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) brand.brand600 else ink.ink700,
        animationSpec = tween(200, easing = EaseOutQuint),
        label = "switchTrack",
    )
    Box(
        modifier = modifier
            .size(width = trackW, height = 24.dp)
            .clip(CircleShape)
            .pressClickable(enabled = enabled, pressedScale = 0.94f, onClickLabel = label) {
                onChange(!checked)
            }
            .alpha(if (enabled) 1f else 0.5f)
            .background(trackColor, CircleShape)
            .semantics {
                role = Role.Switch
                if (label != null) contentDescription = label
                stateDescription = if (checked) "已开启" else "已关闭"
            },
    ) {
        Box(
            Modifier
                // lambda 重载：offset 是动画值，放到布局阶段读，滑动那 200ms 不重组
                .offset { IntOffset(offset.roundToPx(), 2.dp.roundToPx()) }
                .size(knob)
                .shadow(1.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * 分段选择器（对齐 Web `settings-controls.tsx` 的 `Segmented`）：
 * 容器 `rounded-lg` 8dp + `bg-ink-800` + `p-0.5` 2dp；
 * 选中块 `rounded-md` 6dp + `bg-brand-600`（**不是** primary —— 深色主题下 primary
 * 是 brand-400，Web 这里固定 brand-600），文字 12sp，选中白 / 未选中 ink-400。
 *
 * Web 的选中底是一层**独立的滑动指示器**（`translateX` + 200ms），不是每段自己变色；
 * 这里用 BoxWithConstraints 按等分宽度算出偏移，做同样的滑动。
 * 高度 32dp（Web 视觉 28dp + 触控余量），阅读设置与「我的」页共用同一件。
 */
@Composable
fun <T> BrandSegmented(
    options: List<Pair<T, String>>,
    value: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val brand = LocalBrandColors.current
    val ink = LocalInk.current
    val shape = RoundedCornerShape(8.dp)
    val segShape = RoundedCornerShape(6.dp)
    Column(modifier) {
        if (label != null) {
            Text(
                label,
                fontSize = 14.sp,
                color = ink.ink300,
            )
            Spacer(Modifier.height(8.dp))
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(shape)
                .background(ink.ink800, shape)
                // p-0.5：容器内 2dp 的呼吸位，选中块浮在这 2dp 之上
                .padding(2.dp),
        ) {
            val count = options.size.coerceAtLeast(1)
            val segWidth = maxWidth / count
            val selectedIndex = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
            val offset by animateDpAsState(
                targetValue = segWidth * selectedIndex,
                animationSpec = tween(200, easing = EaseOutQuint),
                label = "segIndicator",
            )
            if (enabled) {
                Box(
                    Modifier
                        // 同上：指示条位置随动画走，lambda 重载避免逐帧重组
                        .offset { IntOffset(offset.roundToPx(), 0) }
                        .width(segWidth)
                        .fillMaxHeight()
                        .clip(segShape)
                        .background(brand.brand600, segShape),
                )
            }
            Row(Modifier.fillMaxSize()) {
                options.forEach { (option, text) ->
                    val selected = option == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressClickable(
                                enabled = enabled,
                                pressedScale = 0.98f,
                                onClick = { onChange(option) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text,
                            fontSize = 12.sp, // Web text-xs
                            // Web 的段文字不随选中加粗（只有底色在变），加粗会让选中块
                            // 在滑动指示器到位前后轻微改变宽度，读起来像「抖了一下」
                            fontWeight = FontWeight.Normal,
                            color = when {
                                !enabled -> ink.ink500.copy(alpha = 0.5f)
                                selected -> Color.White
                                else -> ink.ink400
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 高亮色板的一格（对齐 Web 设置页 `ACCENT_OPTIONS`，2026-10-26 F 包按 Web 原值重写）。
 *
 * 三个要点都与 Web 一致：
 *  1. **固定色**，不随明暗主题变 —— Web 这里写死了 `linear-gradient(140deg, …)` 与 hex，
 *     因为色板要「如实展示这个颜色本身」，跟着主题变就没法选了；
 *  2. 渐变角度 **140deg**（比正对角 135° 更偏下一点），标准色太浅的色相（空色/豆沙绿）
 *     渐变到实心主色，白勾在色板上才看得清；
 *  3. 尺寸照 Web：色板本体 **24dp**（`h-6 w-6`），选中环是 2dp 描边 + 与色板之间
 *     一圈卡底间隙；外框 44dp 只是触控命中区（Web 用 `.touch-hit` 的伪元素做同一件事，
 *     Android 直接把命中区做成实心外框）。
 */
@Composable
fun AccentSwatch(
    accent: Accent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = accentSwatchSpec(accent)
    Box(
        modifier = modifier
            .size(44.dp)
            .pressClickable(pressedScale = 0.9f, onClick = onClick)
            .drawBehind {
                if (selected) {
                    // 环画在 40dp 上（半径 19dp、2dp 描边）：外圈 40、内圈 36，
                    // 与 24dp 色板之间留出 6dp 卡底间隙 —— Web 是 2px 间隙 + 2px 描边，
                    // 等比放大到触摸密度后按 44dp 盘面取整。
                    drawCircle(
                        color = spec.ring,
                        radius = size.minDimension / 2f - 2.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .shadow(1.dp, CircleShape, clip = false)
                .drawBehind {
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(spec.from, spec.to),
                            start = Offset(size.width * 0.18f, size.height * 0.12f),
                            end = Offset(size.width * 0.82f, size.height * 0.88f),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                // Web 的 lucide Check 是描边图标（13px / stroke 3.5）；Material 的
                // `Icons.Filled.Check` 是实心块，缩小后笔画比例完全不对，所以自绘。
                Canvas(Modifier.size(13.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = 3.5.dp.toPx()
                    val path = Path().apply {
                        moveTo(w * 0.14f, h * 0.52f)
                        lineTo(w * 0.40f, h * 0.78f)
                        lineTo(w * 0.86f, h * 0.24f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

/** Web `ACCENT_OPTIONS` 的三个固定色（渐变两端 + 选中环 hex）。 */
private data class SwatchSpec(val from: Color, val to: Color, val ring: Color)

private fun accentSwatchSpec(accent: Accent): SwatchSpec = when (accent) {
    // amber: linear-gradient(140deg, #fb923c, #ea580c), ring #f97316
    Accent.AMBER -> SwatchSpec(Color(0xFFFB923C), Color(0xFFEA580C), Color(0xFFF97316))
    // sora: linear-gradient(140deg, #7abedf, #18668c), ring #267eaa
    Accent.SORA -> SwatchSpec(Color(0xFF7ABEDF), Color(0xFF18668C), Color(0xFF267EAA))
    // bean: linear-gradient(140deg, #a3ddb0, #4fa96a), ring #4fa96a
    Accent.BEAN -> SwatchSpec(Color(0xFFA3DDB0), Color(0xFF4FA96A), Color(0xFF4FA96A))
}
