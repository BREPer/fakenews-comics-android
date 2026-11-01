package com.fakenews.comics.ui.reader

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.data.session.FitMode
import com.fakenews.comics.data.session.PageAnimation
import com.fakenews.comics.data.session.PageQuality
import com.fakenews.comics.data.session.ReadDir
import com.fakenews.comics.data.session.ReaderMode
import com.fakenews.comics.data.session.ReaderSettings
import com.fakenews.comics.ui.common.BrandSwitch
import com.fakenews.comics.ui.common.EaseOutQuint
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.theme.AppFontFamily
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 阅读设置（逐值对齐 Web `ReaderSettingsSheet.tsx` + `settings-controls.tsx`）。
 *
 * 布局范式与设置页不同，这是有依据的：Web 那里用的是 `<Row uniform>`（**不带** `block`），
 * 所以任何视口下都是「标签在左、控件列固定 13rem(208dp) 右对齐」——手机上也不例外。
 * 之前 Android 写成「标签在上、分段控件在下」的纵向堆叠，与 Web 是两套排布。
 *
 * 其余逐项：
 *   - 容器 `divide-y divide-ink-800 pb-4`：每行之间一条 `ink-800` 分隔线；
 *   - 行 `px-4 py-3`（16/12dp），标签 14sp，控件列 208dp、右对齐、行内 `gap-x-4`；
 *   - `Segmented` = 8dp 圆角容器 + `ink-800` 底 + 2dp 内衬 + 选中块 `brand-600`（不是 primary）
 *     + 12sp 文字 + 独立滑动指示器（200ms）。选项宽度按**内容比例**分配（Web `stretch`
 *     用 `grow basis-auto`：每段拿「自身文字宽 + 均分剩余」），不是等分；
 *   - 开关是 [BrandSwitch]（44×24dp、开 `brand-600` / 关 `ink-700`），不是 M3 `Switch`；
 *   - 「适应方式」仅在单页模式可用，其余模式下方补三档说明（11sp `ink-500`）。
 */
@Composable
fun ReaderSettingsSheet(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    val ink = LocalInk.current
    val single = settings.mode == ReaderMode.SINGLE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp), // pb-4
    ) {
        SheetRow("翻页模式") {
            StretchSegmented(
                options = listOf(
                    ReaderMode.WEBTOON to "卷轴",
                    ReaderMode.SINGLE to "单页",
                    ReaderMode.DOUBLE to "双页",
                    ReaderMode.SPLIT to "拆分双页",
                ),
                value = settings.mode,
            ) { onChange(settings.copy(mode = it)) }
        }
        SheetDivider()

        SheetRow("阅读方向") {
            StretchSegmented(
                options = listOf(ReadDir.RTL to "右翻（日漫）", ReadDir.LTR to "左翻（欧漫）"),
                value = settings.readDir,
            ) { onChange(settings.copy(readDir = it)) }
        }
        SheetDivider()

        SheetRow("画质") {
            StretchSegmented(
                options = listOf(
                    PageQuality.SMOOTH to "流畅",
                    PageQuality.STANDARD to "标准",
                    PageQuality.ORIGINAL to "原图",
                ),
                value = settings.quality,
            ) { onChange(settings.copy(quality = it)) }
        }
        SheetDivider()

        // 四种，与 Web 的 `pageAnim` 一一对应。选项都在两个字以内，208dp 的分段控件
        // 按内容比例分格不会退化；含义放 hint 里说清楚。
        SheetRow(
            label = "翻页动画",
            hint = when (settings.animation) {
                PageAnimation.PAN -> "整页跟手平移"
                PageAnimation.SLIDE -> "新页轻微推入"
                PageAnimation.FADE -> "新旧页交叉淡入"
                PageAnimation.NONE -> null
            },
        ) {
            StretchSegmented(
                options = listOf(
                    PageAnimation.PAN to "平移",
                    PageAnimation.SLIDE to "轻移",
                    PageAnimation.FADE to "淡入",
                    PageAnimation.NONE to "无",
                ),
                value = settings.animation,
            ) { onChange(settings.copy(animation = it)) }
        }
        SheetDivider()

        SheetRow("适应方式") {
            StretchSegmented(
                options = listOf(
                    FitMode.WIDTH to "宽度",
                    FitMode.HEIGHT to "高度",
                    FitMode.CONTAIN to "完整",
                    FitMode.ORIGINAL to "原始",
                ),
                value = settings.fit,
                enabled = single,
            ) { onChange(settings.copy(fit = it)) }
        }
        SheetDivider()

        if (!single) {
            // Web：px-4 pb-3 text-[11px] leading-relaxed text-ink-500（上下由 divide-y 分隔）
            Text(
                text = when (settings.mode) {
                    ReaderMode.WEBTOON -> "卷轴模式为上下连续滚动，固定按「宽度」适应。"
                    ReaderMode.SPLIT -> "拆分双页把宽幅跨页图（宽大于高）左右对半显示；普通竖版页整页显示。"
                    else -> "双页两图并排，固定按「高度」适应；章节首页与末页单独占一屏。"
                },
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = ink.ink500,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            SheetDivider()
        }

        SheetRow("预加载页数") {
            StretchSegmented(
                options = ReaderSettings.PRELOAD_STEPS.map { it to if (it == 0) "关" else it.toString() },
                value = settings.preload,
            ) { onChange(settings.copy(preload = it)) }
        }
        SheetDivider()

        // 四种模式都支持：卷轴的放大由 WebtoonPager 里的 ZoomCanvas 代管，
        // 放大期间单指拖动是平移画面，缩回 1 倍后恢复成滚动（原先这里是禁用的）
        SheetRow("双指缩放") {
            BrandSwitch(
                checked = settings.pinchZoom,
                onChange = { onChange(settings.copy(pinchZoom = it)) },
                label = "双指缩放",
            )
        }
        SheetDivider()

        // 「保持常亮」Web 放在设置页；Android 没有设置页那一段入口，跟着阅读设置走（见交接文档）
        SheetRow("保持常亮", hint = "阅读时不自动熄屏；关闭后遵循系统休眠时间") {
            BrandSwitch(
                checked = settings.keepScreenOn,
                onChange = { onChange(settings.copy(keepScreenOn = it)) },
                modifier = Modifier.testTag("保持常亮"),
                label = "保持常亮",
            )
        }

        // 底部提示：`px-4 pt-3 text-[11px]`。Web 首句是桌面键盘键位，手机上无意义，
        // 换成同结构的进度说明；pinchZoom 的追加句照旧。
        Text(
            text = buildString {
                append("提示：进度自动记忆，切章/返回不丢失。")
                if (settings.pinchZoom) {
                    append(" 开启双指缩放后：两指缩放，放大状态下单指拖动查看，单击只显隐工具栏。")
                }
            },
            fontSize = 11.sp,
            lineHeight = 18.sp,
            color = ink.ink500,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
    }
}

/**
 * 设置行（Web `Row` 不带 `block` 的形态）：标签左、控件列固定 208dp 右对齐。
 * `px-4 py-3` 16/12dp，中间 `gap-x-4` 16dp。
 */
@Composable
private fun SheetRow(
    label: String,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    val ink = LocalInk.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, fontSize = 14.sp, color = ink.ink100)
            if (hint != null) {
                Text(hint, fontSize = 11.sp, color = ink.ink500)
            }
        }
        // Web 的 `w-[13rem]`：208dp。4 段最长组合实测 200dp，正好放得下不压缩。
        Box(Modifier.width(208.dp), contentAlignment = Alignment.CenterEnd) { control() }
    }
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(
        color = LocalInk.current.ink800,
        thickness = 1.dp,
    )
}

/**
 * 按内容比例分配的分段选择器（Web `Segmented stretch`）。
 *
 * 与设置页那份 [com.fakenews.comics.ui.common.BrandSegmented] 的唯一差别是宽度分配：
 *   - 设置页（Web `fill + stretch`）：手机上是**等分**（`max-sm:basis-0`），现有实现正确；
 *   - 阅读设置（Web 仅 `stretch`）：每段 = 「文字宽 + 20dp 内距」再加上均分的剩余空间
 *     （`grow basis-auto`）。等分会把「拆分双页」压得比其他段一样宽、把「关 / 1 / 3 / 5 / 8」
 *     里的「关」拉得过宽，和 Web 的观感不同。
 *
 * 宽度在组合期用 [rememberTextMeasurer] 量出来（不需要两趟布局），指示器位置/宽度各自
 * 走 200ms 补间 —— 与 Web 那个 `translateX + width` 的绝对定位指示器一致。
 */
@Composable
private fun <T> StretchSegmented(
    options: List<Pair<T, String>>,
    value: T,
    enabled: Boolean = true,
    onChange: (T) -> Unit,
) {
    val brand = LocalBrandColors.current
    val ink = LocalInk.current
    val shape = RoundedCornerShape(8.dp)
    val segShape = RoundedCornerShape(6.dp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(fontSize = 12.sp, fontFamily = AppFontFamily)
    val padH = 10.dp // px-2.5

    BoxWithConstraints(
        modifier = Modifier
            .width(208.dp)
            .height(32.dp)
            .clip(shape)
            .background(ink.ink800, shape)
            // p-0.5：容器内 2dp 的呼吸位，选中块浮在这 2dp 之上
            .padding(2.dp),
    ) {
        val avail = maxWidth
        val count = options.size.coerceAtLeast(1)
        val contents = options.map { (_, text) ->
            with(density) { measurer.measure(text, style).size.width.toDp() } + padH * 2
        }
        val totalContent = contents.fold(0.dp) { acc, w -> acc + w }
        // 内容放不下时（极窄屏 / 超长文案）退化为等分，避免溢出裁掉文字
        val extra = if (totalContent >= avail) 0.dp else (avail - totalContent) / count
        val widths = if (totalContent >= avail) {
            List(count) { avail / count }
        } else {
            contents.map { it + extra }
        }
        val offsets = widths.runningFold(0.dp) { acc, w -> acc + w }
        val index = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
        val left by animateDpAsState(
            targetValue = offsets.getOrElse(index) { 0.dp },
            animationSpec = tween(200, easing = EaseOutQuint),
            label = "segLeft",
        )
        val width by animateDpAsState(
            targetValue = widths.getOrElse(index) { avail },
            animationSpec = tween(200, easing = EaseOutQuint),
            label = "segWidth",
        )
        if (enabled) {
            Box(
                Modifier
                    // lambda 重载：left 是动画值，放到布局阶段读，切换选项那 200ms 不重组
                    .offset { IntOffset(left.roundToPx(), 0) }
                    .width(width)
                    .fillMaxHeight()
                    .clip(segShape)
                    .background(brand.brand600, segShape),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (option, text) ->
                val selected = option == value
                Box(
                    modifier = Modifier
                        .width(widths.getOrElse(i) { avail / count })
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
                        color = when {
                            !enabled -> ink.ink500.copy(alpha = 0.5f)
                            selected -> Color.White
                            else -> ink.ink400
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
