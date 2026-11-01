package com.fakenews.comics.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.ui.common.BookMarkedGlyph
import com.fakenews.comics.ui.common.EaseOutQuint
import com.fakenews.comics.ui.common.EaseSpring
import com.fakenews.comics.ui.common.LibraryGlyph
import com.fakenews.comics.ui.common.pressSelectable
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.LocalIsDark
import com.fakenews.comics.ui.theme.accentTextColor
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * 底栏实际遮住的高度（56dp 内容 + 手势区），由 [MainShell] 用 Scaffold 的 innerPadding 回填。
 *
 * 底栏改成浮在内容之上后，内容不再被 Scaffold 顶开；各页的滚动容器必须把它加进自己的
 * bottom content padding，否则滚到末尾时最后一行会永远压在栏下点不到。
 */
internal val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * 底栏**顶边**到屏幕底边的距离（= 栏内容 56dp + 手势区），由 [MainShell] 回填。
 *
 * [LocalBottomBarInset] 服务的是「内容要停在栏上方」，它把手势区减掉了；
 * 浮层（回顶按钮）不一样 —— 它自己悬在栏的上方，量的是到**屏幕底边**的距离，
 * 所以要含手势区的这份。不给这份的话，`align(BottomEnd)` + 12dp 会把按钮
 * 正好塞进栏的玻璃后面，看起来就像「根本没有这个按钮」（首页回顶实测如此）。
 */
internal val LocalBottomBarTopInset = compositionLocalOf { 0.dp }

/** 底栏内容高度（不含手势区）。Web：`py-2.5` + 20 图标 + 10 标签 ≈ 56。 */
private val BottomBarHeight = 56.dp

/** 宽屏下五个 tab 的最大总宽（每项约 112dp，与手机上的密度一致） */
private val BottomBarMaxWidth = 560.dp

/**
 * 内容区末尾额外留的缝（对齐 Web）。
 *
 * Web 的手机底栏是 `py-2.5 + 图标 20 + 标签 15` ≈ 58px，而内容区 `pb` 写的是 `4.25rem` = 68px
 * —— 最后一行距栏顶**还留 10px**。Android 这边 inset 原本正好等于栏高（0 缝隙），
 * 最后一行紧贴顶部分隔线；栏是半透明毛玻璃，贴这么近读起来局促。
 *
 * 只加这一档，**不要动栏高**：56dp 正好落在 Material 2 / iOS（49pt）那一档，
 * 比 Material 3 的 80dp（再叠 24 手势区 = 104）省 24dp 视野，且与 Web 只差 2dp。
 * 完整对比见 `docs/2026-10-27-AI接手说明.md` §7.11。
 *
 * MainShell 算 LocalBottomBarInset 时也要用它，所以是 internal。
 */
internal val BottomBarContentGap = 10.dp

/**
 * 底部五个 tab（对齐 Web `<nav>` 底栏，2026-10-26 F 包自绘、J 包改成五项）。
 *
 * 与 Material3 `NavigationBar` 的差异都是「观感不是同一个产品」级别的：
 *   1. **没有药丸指示器** —— M3 默认给选中项一个 secondaryContainer 色块，Web 换成
 *      顶部一条 2×32dp 的品牌色短线（scaleX 0→1 淡入），更像「当前页签」而不是一个按钮；
 *   2. **毛玻璃底** —— Web 是 `backdrop-blur-md`（12px）且不写背景色，内容从栏下滚过。
 *      Compose 没有原生 backdrop-filter，这里用 Haze：API 32+ 真模糊，更低版本自动
 *      退化成 `fallbackTint`（92% surface）——否则清晰内容直接透上来，比实底更难看；
 *   3. **选中态只有品牌色文字 + 图标上移 1dp**，没有填充底。
 *
 * 高度 56dp（Web py-2.5 + icon 20 + label 10 ≈ 54），再叠系统手势区。
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun MainBottomBar(tabNav: NavHostController, hazeState: HazeState) {
    val entry by tabNav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    // draw lambda 里不能读 MaterialTheme（不是 @Composable 上下文），颜色先取出来
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val barSurface = MaterialTheme.colorScheme.surface
    // Haze 的「模糊底色」：它会把模糊层（含四周扩边）先铺上这个色再模糊。
    // 必须给页面背景色而不是透明——模糊层被裁在窗口内，屏幕底沿的扩边采不到内容，
    // 给透明的话模糊结果向透明（=未模糊）衰减，手势区那 ~20dp 看起来「没糊、更透」
    // （2026-10-28 模拟器扫色实测：内容带稳定 (232,236,242)，透明底色时底沿跌回
    // (123,188,122)≈85% 原色；给背景色后收在 ~45%，与 Web 持平）。
    // 注意：底沿这条衰减带是 backdrop blur 的物理现象，Chromium（=Web 真源）同样有——
    // 用条纹探针在模拟器 Chrome 实测：视口最后 60px 的条纹振幅从 84 掉到 8。
    // 玻璃内部本就铺满 AmbientBackdrop 的不透明背景，这个色只在扩边处生效，
    // 不影响「纯模糊、零 tint」的对齐口径。详见 docs/2026-10-27-AI接手说明.md §7.12。
    val hazeBase = MaterialTheme.colorScheme.background
    // style 只跟主题色走：remember 住，切 tab 重组时不再重新分配
    val hazeStyle = remember(barSurface, hazeBase) {
        HazeStyle(
            // 零 tint 纯模糊（见上方 hazeBase 注释：底色只用于扩边采样，不参与 tint）
            backgroundColor = hazeBase,
            tints = emptyList(),
            blurRadius = 12.dp,
            noiseFactor = 0f,
            // 不支持模糊的平台（API < 32）退回原来的 92% 底色
            fallbackTint = HazeTint(barSurface.copy(alpha = 0.92f)),
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .hazeChild(
                state = hazeState,
                style = hazeStyle,
                block = {
                    // 采样分辨率减半（像素量 1/4）。实测 Fixed(0.5) 与 None 的底沿衰减带
                    // 完全一致（2026-10-28 扫色），保留降采样只为性能
                    inputScale = HazeInputScale.Fixed(0.5f)
                },
            )
            .drawBehind {
                // 顶部 1dp 细边（Web border-t border-ink-800/70）：把底栏和内容分开，
                // 不然半透明底在浅色主题下会和列表糊在一起
                drawRect(
                    color = dividerColor.copy(alpha = 0.7f),
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            .navigationBarsPadding(),
    ) {
        /*
         * 宽屏（平板 / 展开态折叠屏）把五个 tab 收到 560dp 并居中，玻璃与顶部细边仍铺满。
         * 不限的话 5 个 tab 会被平分成每项 200dp 宽，图标孤零零地漂在中间，
         * 和内容区那条 1080dp 的阅读边界也对不上。
         */
        Row(
            Modifier
                // 顺序不能反：先 widthIn 收窄 max，再 fillMaxWidth 填满收窄后的约束。
                // 反过来（fillMaxWidth().widthIn）时 fillMaxWidth 已把 min/max 都钉成父宽，
                // 后面的 widthIn 只改 max，min > max 会被 coerce 掉，等于没写。
                .widthIn(max = BottomBarMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .height(BottomBarHeight),
        ) {
            TabItem(
                label = "首页",
                selected = current == Tabs.HOME,
                onClick = { tabNav.switchTo(Tabs.HOME) },
            ) { tint ->
                Icon(
                    Icons.Filled.Home,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            TabItem(
                label = "书架",
                selected = current == Tabs.SHELF,
                onClick = { tabNav.switchTo(Tabs.SHELF) },
            ) { tint ->
                // lucide `BookMarked`：核心图标集里没有，自绘（见 NavGlyph.kt）
                BookMarkedGlyph(size = 20.dp, color = tint)
            }
            TabItem(
                label = "搜索",
                selected = current == Tabs.SEARCH,
                onClick = { tabNav.switchTo(Tabs.SEARCH) },
            ) { tint ->
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            TabItem(
                label = "漫画库",
                selected = current == Tabs.LIBRARIES,
                onClick = { tabNav.switchTo(Tabs.LIBRARIES) },
            ) { tint ->
                // lucide `Library`：同样自绘
                LibraryGlyph(size = 20.dp, color = tint)
            }
            TabItem(
                label = "设置",
                selected = current == Tabs.SETTINGS,
                onClick = { tabNav.switchTo(Tabs.SETTINGS) },
            ) { tint ->
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 单个 Tab：顶部指示条（scaleX 动画）+ 图标（选中上移 1dp）+ 10sp 标签。
 * 颜色过渡 200ms —— 与 Web 的 `transition-colors duration-200` 同值。
 *
 * 三个颜色参数带默认值（composable 默认值里读 CompositionLocal 是合法的），
 * 因为五个 tab 全用同一套（Web `AppShell.tsx:116-127` 就写在公共处）：
 * 文字/图标是 `text-brand-400`，而顶部指示条是 `bg-brand-500`——差一档，
 * 指示条更饱和才压得住白底；未选中是 `ink-400`（比 Web 的 ink-500 深一档，见 idleColor 注释）。
 */
@Composable
private fun RowScope.TabItem(
    label: String,
    selected: Boolean,
    // 选中色按明暗取档：浅色主题的白底上 brand-400 只有 2:1 对比度（10sp 的标签
    // 会糊），深色下反过来只有 400 压得住。见 theme.accentTextColor 的注释。
    activeColor: Color = accentTextColor(),
    // 顶部指示条同理：浅色下用 600 才在白毛玻璃上立得住（细条不靠面积取胜）
    barColor: Color = if (LocalIsDark.current) {
        LocalBrandColors.current.brand500
    } else {
        LocalBrandColors.current.brand600
    },
    /*
     * 未选中用 ink-400 而不是 Web 的 ink-500：底栏标签只有 10sp，且它压在毛玻璃上
     * （玻璃后面是列表封面的各种颜色，不是纯白）。ink-500 在浅色主题下是 #6B7280，
     * 4.6:1 的账面对比度够，但 10sp 的字重在玻璃上会灰得发虚 —— 用户实测提过两次。
     * ink 色阶本身已经分明暗两套（浅色 #4B5563 / 深色 #8590A8），所以这里不用再判断主题。
     */
    idleColor: Color = LocalInk.current.ink400,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) activeColor else idleColor,
        animationSpec = tween(200, easing = EaseOutQuint),
        label = "tabTint",
    )
    val barT by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(200, easing = EaseOutQuint),
        label = "tabBar",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        // 图标位移用带一点回弹的曲线（Web ease-spring 200ms）
        animationSpec = tween(200, easing = EaseSpring),
        label = "tabLift",
    )
    // 只在「出现/消失」两个瞬间重组，而不是跟随动画逐帧重组；
    // 动画进行中的逐帧缩放仍走下面的 graphicsLayer（deferred read）
    val barVisible by remember { derivedStateOf { barT > 0.001f } }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressSelectable(selected = selected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 顶部指示条：2dp 高、32dp 宽，选中放大到 1、未选中缩到 0（scaleX 不触发布局）
        if (barVisible) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(32.dp)
                    .height(2.dp)
                    .graphicsLayer {
                        scaleX = barT
                        alpha = barT
                    }
                    .clip(CircleShape)
                    .background(barColor),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 图标由调用方给（有的是 ImageVector，有的是自绘 Canvas），
            // 上移 1dp 的动画统一在这里加，避免每个调用处各写一遍 graphicsLayer
            Box(Modifier.graphicsLayer { translationY = -1.dp.toPx() * lift }) {
                icon(tint)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 10.sp,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
