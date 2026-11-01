package com.fakenews.comics.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.R
import com.fakenews.comics.data.session.Accent

/**
 * 主题（对齐 Web 端 index.css）：
 * - 明暗两套 ink 冷灰色阶（Web 的 --ink-* 变量整体反相）：浅色底 #E9EDF3 / 卡片 #FFF，
 *   深色底 #161923 / 卡片 #232734。早年 Android 端用的是暖 stone 色，和 Web 放在一起
 *   一眼就能看出不是同一个产品。
 * - 高亮色三套（琥珀橙默认 / 空色 / 豆沙绿），与 Web `fakenews-accent` 同值同 legacy 映射，
 *   由「我的 → 外观」切换（Accent 枚举与持久化在 data/session/AppearancePrefs.kt）。
 * - 阅读器固定深色（漫画阅读习惯），但高亮色跟随全局。
 */

/**
 * 一个 accent × 明暗 的品牌色阶。色值照 Web index.css 的 --brand-* 与 --brand-grad-* 抄，
 * 保证「同一台设备上 App 与 Web 观感一致」。注释里保留 Web 的选色依据。
 */
@Immutable
data class BrandColors(
    val brand50: Color,
    val brand100: Color,
    val brand200: Color,
    val brand300: Color,
    val brand400: Color,
    val brand500: Color,
    val brand600: Color,
    val brand700: Color,
    /** 实心按钮 / 选中 chip 的渐变两端（--brand-grad-from/to） */
    val gradFrom: Color,
    val gradTo: Color,
    /** 卡片描边（--card-border）：浅色黑 9%、深色白 7% */
    val cardBorder: Color,
)

private val AmberLight = BrandColors(
    brand50 = Color(0xFFFFF7ED), brand100 = Color(0xFFFFEDD5), brand200 = Color(0xFFFED7AA),
    brand300 = Color(0xFFFDBA74), brand400 = Color(0xFFFB923C), brand500 = Color(0xFFF97316),
    brand600 = Color(0xFFEA580C), brand700 = Color(0xFFC2410C),
    gradFrom = Color(0xFFFB923C), gradTo = Color(0xFFEA580C),
    cardBorder = Color(0x170F172A), // black 9%
)

/** 深色下琥珀橙不重映射（Web 的 .dark 没有 amber 块），只有描边翻转 */
private val AmberDark = AmberLight.copy(cardBorder = Color(0x12FFFFFF)) // white 7%

/*
 * 空色（そらいろ）标准色 #A0D8EF：白字只有 1.55:1，标准色只能占 200 档（浅底强调/色板），
 * 主色顺同一色相（H≈197°）往下压到 500/600 才压得住白字 —— 与 Web 同一条论证。
 */
private val SoraLight = BrandColors(
    brand50 = Color(0xFFF2FAFE), brand100 = Color(0xFFDFF3FC), brand200 = Color(0xFFA0D8EF),
    brand300 = Color(0xFF7ABEDF), brand400 = Color(0xFF3E9CC2), brand500 = Color(0xFF267EAA),
    brand600 = Color(0xFF18668C), brand700 = Color(0xFF115170),
    gradFrom = Color(0xFF267EAA), gradTo = Color(0xFF18668C),
    cardBorder = Color(0x170F172A),
)

/** 深色下两个颜色都要提亮（Web .dark[data-accent]）：深底上不发出来 */
private val SoraDark = BrandColors(
    brand50 = Color(0xFFF2FAFE), brand100 = Color(0xFFDFF3FC), brand200 = Color(0xFFCDEEFA),
    brand300 = Color(0xFFB2E2F4), brand400 = Color(0xFFA0D8EF), brand500 = Color(0xFF4AA6CC),
    brand600 = Color(0xFF2D86AC), brand700 = Color(0xFF18668C),
    gradFrom = Color(0xFF2D86AC), gradTo = Color(0xFF18668C),
    cardBorder = Color(0x12FFFFFF),
)

/** 豆沙绿标准色 #C7EDCC 是护眼底色，同样只能占 200 档，主色压到 500/600 */
private val BeanLight = BrandColors(
    brand50 = Color(0xFFF4FBF6), brand100 = Color(0xFFE6F6EA), brand200 = Color(0xFFC7EDCC),
    brand300 = Color(0xFFA3DDB0), brand400 = Color(0xFF5FAF74), brand500 = Color(0xFF4FA96A),
    brand600 = Color(0xFF3A8B54), brand700 = Color(0xFF2C6F43),
    gradFrom = Color(0xFF4FA96A), gradTo = Color(0xFF2C6F43),
    cardBorder = Color(0x170F172A),
)

private val BeanDark = BrandColors(
    brand50 = Color(0xFFF4FBF6), brand100 = Color(0xFFE6F6EA), brand200 = Color(0xFFC7EDCC),
    brand300 = Color(0xFFA3DDB0), brand400 = Color(0xFFA3DDB0), brand500 = Color(0xFF78C68B),
    brand600 = Color(0xFF4FA96A), brand700 = Color(0xFF3A8B54),
    gradFrom = Color(0xFF4FA96A), gradTo = Color(0xFF3A8B54),
    cardBorder = Color(0x12FFFFFF),
)

fun brandPalette(accent: Accent, dark: Boolean): BrandColors = when (accent) {
    Accent.AMBER -> if (dark) AmberDark else AmberLight
    Accent.SORA -> if (dark) SoraDark else SoraLight
    Accent.BEAN -> if (dark) BeanDark else BeanLight
}

val LocalBrandColors = staticCompositionLocalOf { AmberLight }
val LocalAccent = staticCompositionLocalOf { Accent.AMBER }

/** 当前是否深色主题：环境光/网点这类中性材质要按明暗取不同的色（Web 的 .dark 块同理） */
val LocalIsDark = staticCompositionLocalOf { false }

/**
 * 品牌色「当文字用」的那一档 —— **按明暗取不同的色阶**。
 *
 * `brand-400` 是为「深色底上的亮色文字」定的。把它直接当浅色主题的文字色，
 * 白底上的对比度只有 **2:1**（#FB923C on #F5F6F8），底栏 10sp 的标签、章节列表的
 * 「当前」标记这种小字会糊成一片浅橙；深色下它反而是唯一压得住深底的一档
 * （500/600 在深底上发闷）。所以浅色走 600 档（#EA580C ≈ 4:1）、深色仍走 400 档。
 *
 * 为什么 Web 没这个问题：Web 的 `--brand-400` 明暗共用一个值，它的底栏是 blur 在
 * 浅灰页面上；手机毛玻璃更亮、离眼更近，同一档橙色的可读性更差。**不是抄漏了**，
 * 是场景不同 —— 所以只加在 Android，不反过来去改 Web 的色板。
 *
 * 不要用它做**大面积底色**（按钮、选中 chip）：那是 `brand500/600` 的活，
 * 这里只解决「小字/图标当文字」的可读性。
 */
@Composable
fun accentTextColor(): Color {
    val brand = LocalBrandColors.current
    return if (LocalIsDark.current) brand.brand400 else brand.brand600
}

/**
 * ink 色阶（Web `index.css` 的 `--ink-50` … `--ink-950`，十一档，深色下整体反相）。
 *
 * 为什么要把十一档全搬过来，而不是继续只用「onSurface / onSurfaceVariant」凑：
 * Web 的 ink 阶是**语义化**的 —— 同一个 `text-ink-500` 在浅色下是 #6B7280、深色下是
 * #66718C，而 Material 的 onSurfaceVariant 在浅色下是 ink-400、深色下是 ink-300，
 * 一档错位就会让「卡片副标题」「Chip 未选中」「底栏未选中」三处深浅不一。
 * 既然本项目一切以 Web 为准，色阶就照抄，不再靠 Material 角色猜。
 *
 * 常用的几档含义（Web 侧的原始用法）：
 * - [ink200] 卡片标题 `text-ink-200`、扫描横幅的「正在扫描漫画库」
 * - [ink300] 区块标题、空态标题、Chip 未选中文字、吉祥物剪影（`.mascot` 的 background-color）
 * - [ink400] 错误态的原始消息、`btn-ghost` 文字
 * - [ink500] 卡片副标题、底栏未选中、记录行 meta
 * - [ink600] 破图图标、列表底部「已经到底了」
 * - [ink700] 极淡的分隔线
 * - [ink800] 卡片/弹层的底与描边、chip-off 的 ring
 * - [ink900] 卡片底（浅色下就是纯白）
 */
@Immutable
data class InkColors(
    val ink50: Color,
    val ink100: Color,
    val ink200: Color,
    val ink300: Color,
    val ink400: Color,
    val ink500: Color,
    val ink600: Color,
    val ink700: Color,
    val ink800: Color,
    val ink900: Color,
    val ink950: Color,
)

private val InkLight = InkColors(
    ink50 = Color(0xFF0B1220),
    ink100 = Color(0xFF111827),
    ink200 = Color(0xFF1F2937),
    ink300 = Color(0xFF374151),
    ink400 = Color(0xFF4B5563),
    ink500 = Color(0xFF6B7280),
    ink600 = Color(0xFF8C94A2),
    ink700 = Color(0xFFCBD0DA),
    ink800 = Color(0xFFE4E8F0),
    ink900 = Color(0xFFFFFFFF),
    ink950 = Color(0xFFF5F6F8),
)
private val InkDark = InkColors(
    ink50 = Color(0xFFF6F7F9),
    ink100 = Color(0xFFECEEF2),
    ink200 = Color(0xFFD5D9E2),
    ink300 = Color(0xFFB0B8C8),
    ink400 = Color(0xFF8590A8),
    ink500 = Color(0xFF66718C),
    ink600 = Color(0xFF515A72),
    ink700 = Color(0xFF42495C),
    ink800 = Color(0xFF393E4D),
    ink900 = Color(0xFF232734),
    ink950 = Color(0xFF161923),
)

val LocalInk = staticCompositionLocalOf { InkLight }

/* ---------------- ink 色阶（Web index.css :root / .dark 原值） ---------------- */

private val InkBgLight = Color(0xFFE9EDF3) // --app-bg
private val InkBgDark = Color(0xFF161923)

@Composable
private fun lightScheme(b: BrandColors) = lightColorScheme(
    primary = b.brand600,
    onPrimary = Color.White,
    primaryContainer = b.brand100,
    onPrimaryContainer = b.brand700,
    secondary = b.brand500,
    onSecondary = Color.White,
    secondaryContainer = b.brand100,
    onSecondaryContainer = b.brand700,
    tertiary = b.brand400,
    onTertiary = Color.White,
    tertiaryContainer = b.brand50,
    onTertiaryContainer = b.brand700,
    background = InkBgLight,
    onBackground = Color(0xFF111827), // ink-100
    surface = Color(0xFFFFFFFF), // ink-900
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE4E8F0), // ink-800
    onSurfaceVariant = Color(0xFF4B5563), // ink-400
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerHigh = InkBgLight,
    surfaceContainerHighest = Color(0xFFE4E8F0),
    inverseSurface = Color(0xFF232734),
    inverseOnSurface = Color(0xFFECEEF2),
    outline = Color(0xFF8C94A2), // ink-500
    outlineVariant = Color(0xFFE4E8F0),
    error = Color(0xFFB91C1C),
    onError = Color.White,
)

@Composable
private fun darkScheme(b: BrandColors) = darkColorScheme(
    primary = b.brand400,
    onPrimary = Color(0xFF12141A),
    primaryContainer = b.brand700,
    onPrimaryContainer = b.brand100,
    secondary = b.brand500,
    onSecondary = Color(0xFF12141A),
    secondaryContainer = b.brand700,
    onSecondaryContainer = b.brand100,
    tertiary = b.brand300,
    onTertiary = Color(0xFF12141A),
    tertiaryContainer = b.brand700,
    onTertiaryContainer = b.brand100,
    background = InkBgDark,
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF232734), // ink-900
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF393E4C), // ink-800
    onSurfaceVariant = Color(0xFFB0B8C8), // ink-300
    surfaceContainerLowest = Color(0xFF101320),
    surfaceContainerLow = Color(0xFF1B1F2B),
    surfaceContainer = Color(0xFF232734),
    surfaceContainerHigh = Color(0xFF2A2F3E),
    surfaceContainerHighest = Color(0xFF393E4C),
    inverseSurface = Color(0xFFE9EDF3),
    inverseOnSurface = Color(0xFF1F2937),
    outline = Color(0xFF515A72), // ink-600
    outlineVariant = Color(0xFF2E3342),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
)

/* ---------------- 主题切换的全局颜色过渡 ---------------- */

/**
 * 切明暗主题 / 换高亮色时的全局颜色过渡（对齐 Web `.theme-transition`，200ms）。
 *
 * Web 的做法是切换那一刻给 `<html>` 挂一个类，让 background-color / border-color /
 * color / fill / stroke 全部走 0.2s 过渡（`index.css:841-855` + `theme.ts`）。
 * Android 没有全局 CSS，等价做法是把三套色板里的每个颜色都用 [animateColorAsState]
 * 驱动一遍 —— 它们都是 CompositionLocal 的值，这里一动，所有读它的组件自动插值。
 *
 * **三套都要动**：只动 ink 而不管 M3 的 colorScheme，骨架、进度圈、弹层底色会硬跳，
 * 其余平滑 —— 一半卡住了比全都硬跳更难看。
 *
 * 稳态开销为零：动画结束后 animateColorAsState 返回稳定值，data class 的 equals
 * 让 CompositionLocalProvider 不再向下失效，不会出现「换完主题一直在重组」。
 */
private const val THEME_FADE_MS = 200

private val ThemeEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
private fun fade(target: Color, spec: AnimationSpec<Color>, label: String): Color =
    animateColorAsState(targetValue = target, animationSpec = spec, label = label).value

@Composable
private fun animatedInk(target: InkColors, spec: AnimationSpec<Color>) = InkColors(
    ink50 = fade(target.ink50, spec, "ink50"),
    ink100 = fade(target.ink100, spec, "ink100"),
    ink200 = fade(target.ink200, spec, "ink200"),
    ink300 = fade(target.ink300, spec, "ink300"),
    ink400 = fade(target.ink400, spec, "ink400"),
    ink500 = fade(target.ink500, spec, "ink500"),
    ink600 = fade(target.ink600, spec, "ink600"),
    ink700 = fade(target.ink700, spec, "ink700"),
    ink800 = fade(target.ink800, spec, "ink800"),
    ink900 = fade(target.ink900, spec, "ink900"),
    ink950 = fade(target.ink950, spec, "ink950"),
)

@Composable
private fun animatedBrand(target: BrandColors, spec: AnimationSpec<Color>) = BrandColors(
    brand50 = fade(target.brand50, spec, "b50"),
    brand100 = fade(target.brand100, spec, "b100"),
    brand200 = fade(target.brand200, spec, "b200"),
    brand300 = fade(target.brand300, spec, "b300"),
    brand400 = fade(target.brand400, spec, "b400"),
    brand500 = fade(target.brand500, spec, "b500"),
    brand600 = fade(target.brand600, spec, "b600"),
    brand700 = fade(target.brand700, spec, "b700"),
    gradFrom = fade(target.gradFrom, spec, "bGradFrom"),
    gradTo = fade(target.gradTo, spec, "bGradTo"),
    cardBorder = fade(target.cardBorder, spec, "bCardBorder"),
)

/** 只覆盖实际用到的字段：没列出的（如 surfaceTint）在切换时硬跳，但没人读它们。 */
@Composable
private fun animatedScheme(target: ColorScheme, spec: AnimationSpec<Color>) = target.copy(
    primary = fade(target.primary, spec, "primary"),
    onPrimary = fade(target.onPrimary, spec, "onPrimary"),
    primaryContainer = fade(target.primaryContainer, spec, "primaryContainer"),
    onPrimaryContainer = fade(target.onPrimaryContainer, spec, "onPrimaryContainer"),
    secondary = fade(target.secondary, spec, "secondary"),
    onSecondary = fade(target.onSecondary, spec, "onSecondary"),
    secondaryContainer = fade(target.secondaryContainer, spec, "secondaryContainer"),
    onSecondaryContainer = fade(target.onSecondaryContainer, spec, "onSecondaryContainer"),
    tertiary = fade(target.tertiary, spec, "tertiary"),
    onTertiary = fade(target.onTertiary, spec, "onTertiary"),
    tertiaryContainer = fade(target.tertiaryContainer, spec, "tertiaryContainer"),
    onTertiaryContainer = fade(target.onTertiaryContainer, spec, "onTertiaryContainer"),
    background = fade(target.background, spec, "background"),
    onBackground = fade(target.onBackground, spec, "onBackground"),
    surface = fade(target.surface, spec, "surface"),
    onSurface = fade(target.onSurface, spec, "onSurface"),
    surfaceVariant = fade(target.surfaceVariant, spec, "surfaceVariant"),
    onSurfaceVariant = fade(target.onSurfaceVariant, spec, "onSurfaceVariant"),
    surfaceContainerLowest = fade(target.surfaceContainerLowest, spec, "scLowest"),
    surfaceContainerLow = fade(target.surfaceContainerLow, spec, "scLow"),
    surfaceContainer = fade(target.surfaceContainer, spec, "sc"),
    surfaceContainerHigh = fade(target.surfaceContainerHigh, spec, "scHigh"),
    surfaceContainerHighest = fade(target.surfaceContainerHighest, spec, "scHighest"),
    inverseSurface = fade(target.inverseSurface, spec, "inverseSurface"),
    inverseOnSurface = fade(target.inverseOnSurface, spec, "inverseOnSurface"),
    outline = fade(target.outline, spec, "outline"),
    outlineVariant = fade(target.outlineVariant, spec, "outlineVariant"),
    error = fade(target.error, spec, "error"),
    onError = fade(target.onError, spec, "onError"),
)

/**
 * 内嵌中文正文字体：Noto Sans SC（思源黑体，SIL OFL，许可全文见
 * `assets/licenses/noto-sans-sc-OFL.txt`）。
 *
 * Web 的字体栈是 PingFang SC → 微软雅黑 → system-ui：前两个是苹果 / 微软的商用字体，
 * 不能随包分发，所以只能取同属「低对比黑体」的开源字体。不内嵌的后果是同一版 App
 * 在 Pixel（Noto Sans CJK）、小米（MiSans）、华为（HarmonyOS Sans）上是三种字形、
 * 三种字宽 —— 和 Web 并排时差异就来自这里，而不是字号。
 *
 * 用可变字重单文件（17.7MB）覆盖 400/500/600/700，四个静态字重各 10MB+ 会撑到 40MB。
 */
@OptIn(ExperimentalTextApi::class)
val AppFontFamily = FontFamily(
    Font(
        R.font.noto_sans_sc,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.noto_sans_sc,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.noto_sans_sc,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.noto_sans_sc,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * 字距归零（对齐 Web）：Tailwind 的默认 letter-spacing 是 `normal`(0)，全站只有 5 处
 * 显式写了 `tracking-*`。Material 的默认 Typography 带 Roboto 字距（bodyLarge 0.5sp、
 * bodyMedium 0.25sp、labelSmall 0.5sp、titleSmall 0.1sp），而 MaterialTheme 会把
 * bodyLarge 作为全局默认文本样式下发 —— 于是每个只写了 `fontSize` 的 Text 都会吃到
 * 0.5sp。汉字是等宽方块，加字距直接表现为「每两个字之间多一条缝」，和 Web 并排很显眼。
 *
 * 所以 15 档全部清零；需要字距的地方（设置页区块标题 0.3sp、阅读器提示条 0.12sp、
 * 首页「继续阅读」-0.35sp）在调用处单独给，与 Web 的 `tracking-*` 一一对应。
 */
private fun TextStyle.webAligned() = copy(
    letterSpacing = 0.sp,
    fontFamily = AppFontFamily,
    // 行高也交还字体自然比例（Noto Sans SC hhea = 1.448×）。
    //
    // 为什么必须清掉：Compose 的 lineHeight 是**绝对值**（sp），一旦在 Typography 里定成
    // Material 的 24sp，调用点只改 `fontSize =` 时它**不会跟着缩放** —— 于是 11sp 的脚注
    // 也顶着 24sp 的行盒。Web 那边 line-height 是继承/相对的（text-sm 20px、text-[11px]
    // 16.5px），天然随字号走，这才是「两边字体对不上」的真正来源（字形、字距其实早就同步了）。
    // 泄漏面实测：首页一屏 34 个文本节点里 20 个都是 24dp 高。
    // 自然比例算下来：11→15.9、14→20.3、16→23.2、18→26.1，与 Web 的 16.5/20/24/28 基本重合；
    // 少数需要精确控制的调用点（14 处）本来就显式写了 lineHeight，不受影响。
    lineHeight = TextUnit.Unspecified,
)

/**
 * Web `tabular-nums`：纯数字文本要等宽（页码、章节序号、端口、大小、时长）。
 * 不等宽时「9/12 → 10/12」会整串左右跳一下，Web 用 class，Compose 只能走 fontFeatureSettings。
 *
 * 用法：`Text(..., style = TabularNums, fontSize = 11.sp)` —— 它只提供字族与 tnum，
 * 字号/颜色/字重仍由调用点的参数决定。
 */
val TabularNums = TextStyle(fontFeatureSettings = "tnum", fontFamily = AppFontFamily)

private val FakenewsTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.webAligned(),
        displayMedium = t.displayMedium.webAligned(),
        displaySmall = t.displaySmall.webAligned(),
        headlineLarge = t.headlineLarge.webAligned(),
        headlineMedium = t.headlineMedium.webAligned(),
        headlineSmall = t.headlineSmall.webAligned(),
        titleLarge = t.titleLarge.webAligned(),
        titleMedium = t.titleMedium.webAligned(),
        titleSmall = t.titleSmall.webAligned(),
        bodyLarge = t.bodyLarge.webAligned(),
        bodyMedium = t.bodyMedium.webAligned(),
        bodySmall = t.bodySmall.webAligned(),
        labelLarge = t.labelLarge.webAligned(),
        labelMedium = t.labelMedium.webAligned(),
        labelSmall = t.labelSmall.webAligned(),
    )
}

@Composable
fun FakenewsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.AMBER,
    content: @Composable () -> Unit,
) {
    // 200ms 的过渡 spec。首次组合时 animateColorAsState 直接取目标值（没有「从旧色
    // 淡入」的过程），所以冷启动不会看到一次莫名其妙的整屏渐显。
    val spec: AnimationSpec<Color> = tween(THEME_FADE_MS, easing = ThemeEase)
    val brand = animatedBrand(brandPalette(accent, darkTheme), spec)
    val scheme = animatedScheme(if (darkTheme) darkScheme(brand) else lightScheme(brand), spec)
    val ink = animatedInk(if (darkTheme) InkDark else InkLight, spec)
    CompositionLocalProvider(
        LocalBrandColors provides brand,
        LocalAccent provides accent,
        LocalIsDark provides darkTheme,
        LocalInk provides ink,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FakenewsTypography,
            // 卡片/面板的圆角口径对齐 Web：rounded-xl 12（小件）/ rounded-2xl 16（区块）
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
