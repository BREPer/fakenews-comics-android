package com.fakenews.comics.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.LocalIsDark
import kotlin.math.sqrt

/**
 * 页面底色 —— 纯色，**不要**再往这里画环境光/渐变。
 *
 * Web 的 `body::before` 曾写过一层品牌色（橙）薄雾 + 一层冷色补光，但它从未被画出来
 * （`body` 的不透明底色把 `z-index:-1` 的伪元素整片盖住）；为避免这段死代码哪天被
 * 「修」成真的橙雾，已从 web 端一并删除。两端现在都是纯 --app-bg / colorScheme.background。
 *
 * 详情页的 `.page-ambient` 是另一回事：中性色、且父级带 opacity 动画形成层叠上下文，
 * 它是真实渲染的 —— 那层薄雾仍由 [PageAmbient] 负责，不要一并删掉。
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        content = content,
    )
}

/* ================= 详情页顶部环境光 ================= */

/**
 * 一层色团。数值全是**盒子尺寸的比例**，与 CSS 一一对应：
 * `radial-gradient(ellipse <rx> <ry> at <fx> <fy>, color, transparent <end>)`。
 */
private class GlowSpec(
    val color: Color,
    val alpha: Float,
    val fx: Float,
    val fy: Float,
    val rx: Float,
    val ry: Float,
    /** CSS 的 `transparent N%`：径向走到 N% 处归零 */
    val end: Float,
)

/**
 * 色团的衰减曲线（横轴是「占 [GlowSpec.end] 的比例」）。
 *
 * CSS 的 `color → transparent 72%` 是**线性**插值：斜率恒定，到 72% 处突然变成 0，
 * 视线在那一圈上会读出一条边（马赫带）。大面积浅色渐变上这条边很明显，
 * 也就是「光晕边缘生硬」的主要来源。
 *
 * 换成下面这条先缓后拖尾的曲线后，末端斜率趋近 0，和底色自然接上；
 * 中段（0.3–0.6）反而比线性略亮，光晕看着更"涨"而不是更"小"。
 * 只在 4 个点上采值，交给着色器线性插值 —— 分档再多也只是更平滑，视觉没有增量。
 */
private val GlowStops = floatArrayOf(0f, 0.35f, 0.62f, 0.84f, 1f)
private val GlowAlphaCurve = floatArrayOf(1f, 0.58f, 0.24f, 0.06f, 0f)

/** 网点的 mask 比色团收得更快（Web `ellipse 106% 88% at 50% -6%`，22% → 60%） */
private const val GRAIN_CX = 0.50f
private const val GRAIN_CY = -0.06f
private const val GRAIN_RX = 1.06f
private const val GRAIN_RY = 0.88f
private const val GRAIN_FULL = 0.22f
private const val GRAIN_END = 0.60f

/**
 * 详情页顶部环境光（对齐 Web `.page-ambient`）：四层中性色椭圆渐变 + 印刷网点。
 *
 * 刻意**不**用品牌色（Web 同款论证：高亮色是交互语言，背景氛围抢用它会被稀释），
 * 只走中性色 —— 换书、换高亮色都不变，只随明暗主题走。
 * 网点（漫画的网点纸）是这个项目最贴题的质感语言：撒一层极淡圆点后，
 * 大面积渐变从「塑料感」变成「材质」。
 *
 * **必须从屏幕最顶端开始画**：光晕的中心都在盒子顶边以上（fy 是负的），
 * 顶边本身还有三成左右的亮度。之前它挂在详情页顶栏以下的容器里，光晕的上沿
 * 正好落在顶栏底边，成了一条横贯整屏的硬边（实测那一行像素从 (22,25,35)
 * 跳到 (40,45,58)）。挂载位置见 `AppNav.DetailRoute`：那里不再整页让出状态栏，
 * 顶栏的避让由 DetailScreen 自己用 windowInsetsPadding(statusBars) 做。
 */
@Composable
fun PageAmbient(modifier: Modifier = Modifier, height: Dp = 288.dp) {
    val dark = LocalIsDark.current
    val ink = LocalInk.current
    // Web: 浅色 glow-1 白 / glow-2 #E2E6EE；深色 glow-1 ink-500 / glow-2 ink-600
    val glow1 = if (dark) ink.ink500 else Color.White
    val glow2 = if (dark) ink.ink600 else Color(0xFFE2E6EE)
    val grain = if (dark) Color.White else ink.ink700
    /*
     * 两档不透明度是**分主题**的，浅色 0.34/0.20、深色 0.24/0.16（Web `--page-glow-a/b`）。
     * 之前这里只有一套（照抄了浅色的当常量），深色下光晕比设计强了四成 ——
     * 深色底本来就吃不住这么多光。
     */
    val a = if (dark) 0.24f else 0.34f
    val b = if (dark) 0.16f else 0.20f
    val grainAlpha = if (dark) 0.07f else 0.10f

    // 四层色团：位置与衰减各不相同，叠出此起彼伏的层次（Web 原值）
    val glows = listOf(
        GlowSpec(glow2, a * 0.8f, 0.32f, -0.22f, 0.80f, 1.06f, 0.72f),
        GlowSpec(glow1, a, 0.60f, -0.16f, 0.60f, 0.90f, 0.68f),
        GlowSpec(glow1, b, 0.91f, -0.12f, 0.38f, 0.74f, 0.64f),
        GlowSpec(glow1, b * 0.72f, 0.13f, 0.02f, 0.24f, 0.50f, 0.56f),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val brushes = glows.map { g ->
                    // 曲线是按「占 end 的比例」给的，shader 的比例轴是半径，所以乘回 end
                    val stops = FloatArray(GlowStops.size) { GlowStops[it] * g.end }
                    g to Brush.radialGradient(
                        colorStops = Array(stops.size) { i ->
                            stops[i] to g.color.copy(alpha = g.color.alpha * g.alpha * GlowAlphaCurve[i])
                        },
                        center = Offset.Zero,
                        radius = w * g.rx,
                    )
                }
                // 6px 网格 + 1.8px 点：再密成噪点、再疏成图案（Web 原值）
                val gap = 6.dp.toPx()
                val dot = 1.8.dp.toPx()
                val cols = (w / gap).toInt()
                val rows = (h / gap).toInt()
                val gcx = w * GRAIN_CX
                val gcy = h * GRAIN_CY
                val grx = w * GRAIN_RX
                val gry = h * GRAIN_RY

                onDrawBehind {
                    /*
                     * 椭圆用「圆 + 纵向拉伸」画：`Brush.radialGradient` 只有圆，
                     * 而 Web 的四层色团全是 `ellipse`。早先用 `radius = max(rx, ry)`
                     * 凑，窄屏上等于把椭圆吹成大圆 —— 横向溢到屏幕外、纵向又伸得过远，
                     * 整片糊成一层均匀的雾，中心与衰减都读不出来。
                     *
                     * 拉伸只作用于**这一层**（withTransform 会还原），
                     * 渐变本身仍是圆，圆之外不画东西，所以不存在「铺满整块矩形」的硬边。
                     */
                    brushes.forEach { (g, brush) ->
                        withTransform({
                            translate(w * g.fx, h * g.fy)
                            scale(1f, g.ry / g.rx, pivot = Offset.Zero)
                        }) {
                            // center 必须显式给 Offset.Zero：drawCircle 默认画在画布中心
                            drawCircle(brush = brush, radius = w * g.rx, center = Offset.Zero)
                        }
                    }

                    for (r in 0..rows) {
                        val y = r * gap + gap / 2f
                        for (c in 0..cols) {
                            val x = c * gap + gap / 2f
                            // 网点按**椭圆距离**衰减：只按行衰减的话，左右两端会把
                            // 网点一直撒到屏幕边缘，落在纯底色上就是一片来路不明的麻点
                            val nx = (x - gcx) / grx
                            val ny = (y - gcy) / gry
                            val d = sqrt(nx * nx + ny * ny)
                            if (d >= GRAIN_END) continue
                            val t = ((d - GRAIN_FULL) / (GRAIN_END - GRAIN_FULL)).coerceIn(0f, 1f)
                            drawCircle(
                                color = grain.copy(alpha = grainAlpha * (1f - t)),
                                radius = dot / 2f,
                                center = Offset(x, y),
                            )
                        }
                    }
                }
            },
    )
}
