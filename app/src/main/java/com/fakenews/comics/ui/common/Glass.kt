package com.fakenews.comics.ui.common

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * 玻璃浮层的统一底色。Web 的几处玻璃取值略有出入（续读卡 `rgb(18 20 26)`、
 * 轻提示 `rgb(20 22 28)`，差 2 个色阶不可辨），这里统一取 `0xFF14161C`，
 * 与 Android 一直用的值一致。
 */
val GlassTint = Color(0xFF14161C)

/**
 * 全站统一的「玻璃浮层」外观 = 背景模糊 + 一层按 Web 取值的半透明深色 tint，
 * 等价于 CSS 的 `background: rgb(...) / a` + `backdrop-filter: blur(n)`。
 *
 * **为什么必须带模糊，而不是把 alpha 调高**：tint 只有 55%~72% 不透明，没有模糊时
 * 身下的内容会原样透出来。浮层贴在页面下缘时，内容边界往往横穿浮层 —— 上截透出纸面、
 * 下截压在黑底上，就显出一条硬分界（2026-10-30 续读卡那条「白色长方形」）。
 * 调成近乎不透明确实能消掉它，但玻璃感也没了（用户当场否掉）。模糊把分界糊成渐变，
 * 这才是 Web 的做法。
 *
 * [state] 为 null 时（调用方没准备采样源、或不支持 RenderEffect 的机器）退回 96%
 * 近不透明底：宁可没有玻璃感，也不能透出清晰内容。
 *
 * 圆角在这里 `clip` 完成，调用方的 `.border(...)` 应写在**参数之后**——边框会被裁掉
 * 外侧半像素；1dp 在 xxhdpi 上是 2.6px，裁半反而更接近 Web 的 1px。
 *
 * 用法：
 * ```
 * Modifier.glass(hazeState, RoundedCornerShape(14.dp), alpha = 0.72f)
 *     .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
 * ```
 * 采样源由调用方在内容层挂 `Modifier.haze(state)`；**不要让它常驻**（见 ReaderScreen
 * 里那段注释：常驻会让静止页面持续重绘）。
 */
@Composable
fun Modifier.glass(
    state: HazeState?,
    shape: Shape,
    tint: Color = GlassTint,
    alpha: Float = 0.72f,
    blurRadius: Dp = 18.dp,
): Modifier {
    if (state == null) {
        return clip(shape).background(tint.copy(alpha = 0.96f), shape)
    }
    val style = remember(tint, alpha, blurRadius) {
        HazeStyle(
            // 只用于模糊层扩边的预填充（不参与最终合成）：给 tint 本身，
            // 给透明的话扩边处会向「未模糊」衰减，看起来没糊上
            backgroundColor = tint,
            tints = listOf(HazeTint(tint.copy(alpha = alpha))),
            blurRadius = blurRadius,
            noiseFactor = 0f,
            fallbackTint = HazeTint(tint.copy(alpha = 0.96f)),
        )
    }
    return clip(shape).hazeChild(state = state, style = style)
}
