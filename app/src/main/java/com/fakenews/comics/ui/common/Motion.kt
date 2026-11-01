package com.fakenews.comics.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/* 动画曲线，与 Web index.css 的 --ease-out / --ease-spring 同源 */
val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * 过冲曲线的**安全**实现：控制点 y 超过 1 的贝塞尔（过冲就是这么来的）。
 *
 * 不能直接写 `CubicBezierEasing(0.34f, 1.42f, 0.64f, 1f)`：Compose 内部用牛顿迭代解
 * `x(t) = fraction`，y 越界会让曲线末端极平缓，迭代出的 t 落到区间外就直接抛
 * `IllegalArgumentException: The cubic curve with parameters (0.34, 1.42, 0.64, 1.0)
 * has no solution at 0.99999994` —— 实测把 `animator_duration_scale` 调到 10（动画拉长、
 * 采样点更靠近 1）必崩，且是 FATAL，整个进程被杀。
 * 二分法只要 x(t) 单调就无条件收敛（控制点 x 落在 [0,1] 内即满足），不会失败。
 * 代价是每帧几十次乘加，对这种 200ms 级的短动画可以忽略。
 */
fun bezierEasing(x1: Float, y1: Float, x2: Float, y2: Float): Easing = Easing { fraction ->
    if (fraction <= 0f) return@Easing 0f
    if (fraction >= 1f) return@Easing 1f
    var lo = 0f
    var hi = 1f
    var t = fraction
    // 24 次二分 → 精度 2^-24 ≈ 6e-8，远小于一帧的位移量
    repeat(24) {
        t = (lo + hi) * 0.5f
        val u = 1f - t
        val x = 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t
        if (x < fraction) lo = t else hi = t
    }
    val u = 1f - t
    3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t
}

/** Web `--ease-spring`：底栏选中项的抬起（200ms 带回弹） */
val EaseSpring = bezierEasing(0.34f, 1.42f, 0.64f, 1f)

/**
 * Web .press / .btn 的按压回弹：点按轻微缩小、松手弹回，给所有可点元素一致的触感。
 *
 * 必须与 clickable 合并成一个修饰符：两者共用同一个 interactionSource，
 * 分开写的话按压状态永远采集不到。不叠 ripple —— Web 的按压反馈就是这一下缩放，
 * 两套反馈同时出现会打架。
 *
 * Web 其实有两条口径：通用 `.press` 是 100ms / 0.96，`.btn` 是 150ms / 0.97。
 * 默认取前者；按钮类传 `pressedScale = 0.97f, durationMillis = 150`。
 */
@Composable
fun Modifier.pressClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    pressedScale: Float = 0.96f,
    durationMillis: Int = 100,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 只拿到 State，**不在组合阶段读 `.value`**：读了的话按压这 100ms 里每一帧都会
    // 重组整个被包裹的子树（一张卡＝封面 ImageRequest + 两行文本重新布局）。
    // 放进 graphicsLayer 的 lambda 里读，状态变化只让这一层重绘。
    val scale = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        label = "pressScale",
    )
    return this
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
}

/**
 * 底部 Tab 的整块按压回弹（Web `active:scale-[0.95]`）。
 *
 * 用 [selectable] 而不是 clickable：底栏项要给读屏「选中态 + Tab 角色」，
 * NavigationBarItem 就是这么做的；换自绘底栏后这层语义得自己补，否则无障碍退化成
 * 四个普通按钮。indication 置空 —— 缩放本身就是反馈，再叠 ripple 会打架。
 */
@Composable
fun Modifier.pressSelectable(
    selected: Boolean,
    onClick: () -> Unit,
    role: Role = Role.Tab,
    pressedScale: Float = 0.95f,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 同 [pressClickable]：State 不在组合阶段读值，避免按压期间每帧重组底栏项
    val scale = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "tabScale",
    )
    return this
        .selectable(
            selected = selected,
            interactionSource = interaction,
            indication = null,
            role = role,
            onClick = onClick,
        )
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
}

/**
 * Web .anim-card-in：卡片入场（淡入 + 上浮 10dp），按序号错峰（前 12 个各 26ms），
 * 首屏是「依次浮现」而不是整片同时闪出。340ms / ease-out-quint。
 *
 * 与按压回弹分居两层（外层入场、内层按压）：两套 transform 挂在同一节点上会互相覆盖
 * —— Web 的 ComicCard 注释里是同一个理由。
 *
 * **只播一次**（[played]）：Lazy 列表的 item 滚出视口会销毁组合，滚回来重新组合，
 * 用 `remember` 记状态的话每次回看都重播一遍 340ms 淡入。Web 那边用的是 CSS animation，
 * DOM 一直在，所以只播一次 —— 这个差异不是抄漏了，是 Compose 的 Lazy 生命周期决定的。
 * `rememberSaveable` 的状态由 LazyList 按 item key 保留（列表必须提供 key，否则退化成
 * 按位置保存，滚回来仍会重播）。
 */
@Composable
fun CardEntrance(index: Int, content: @Composable BoxScope.() -> Unit) {
    // 是否已经播过入场。注意不能用 remember：item 被回收后 remember 就没了。
    var played by rememberSaveable { mutableStateOf(false) }
    // 重建时按 played 给初值，避免「先闪一下再跳到终态」
    val anim = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        anim.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = 340,
                delayMillis = index.coerceIn(0, 11) * 26,
                easing = EaseOutQuint,
            ),
        )
        played = true
    }
    Box(
        modifier = Modifier.graphicsLayer {
            /*
             * 动画值**只在绘制阶段读**。
             *
             * 早先写成 `val t = anim.value` + 引用 `t`：那是在组合阶段读 State，于是
             * 这 340ms 里每一帧都会重组整张卡片 —— 一屏 12 张卡就是 12×~20 次重组，
             * 每次都连带 Coil 的 ImageRequest 相等性比较与两行文本的重新布局。
             * 首屏「浮现」那一下的掉帧就是从这来的。放进 lambda 后，每帧只让这一层
             * 重绘（改 alpha / translationY 不触发组合与布局）。
             */
            val t = anim.value
            alpha = t
            translationY = 10.dp.toPx() * (1f - t)
        },
        content = content,
    )
}
/**
 * Web .animate-rise：通用入场（淡入 + 上浮 8dp，340ms / ease-out）。
 * 用于「整块结果替换」（空态 / 错误态）与列表行。时长与卡片入场同源（都是 0.34s），
 * 区别只在不做错峰 —— 行是逐条出现的，不需要 26ms 的台阶。
 *
 * 与 [CardEntrance] 同理，用 `rememberSaveable` 记住「播过了」——记录列表几百行，
 * 滚上滚下时每行都重播 340ms 淡入既是视觉噪音也是无谓的逐帧重绘。
 */
@Composable
fun RiseIn(delayMillis: Int = 0, content: @Composable BoxScope.() -> Unit) {
    var played by rememberSaveable { mutableStateOf(false) }
    val anim = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        anim.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = 340,
                delayMillis = delayMillis.coerceAtLeast(0),
                easing = EaseOutQuint,
            ),
        )
        played = true
    }
    Box(
        modifier = Modifier.graphicsLayer {
            // 同 [CardEntrance]：绘制阶段读值，否则入场期间每帧重组整行的内容
            val t = anim.value
            alpha = t
            translationY = 8.dp.toPx() * (1f - t)
        },
        content = content,
    )
}

/**
 * 首次显示后延迟 [delayMillis] 再淡入（Web `useDelayedShow`）。
 *
 * 等待态的吉祥物用它：局域网里等待只有几十毫秒，立刻出现等于一闪而过的噪点；
 * 只有真的慢下来（NAS 硬盘休眠实测 0.9~2.5s）才请它出来。阈值 600ms 是 Web 三档限速
 * 实测出来的中间空档（见 Web `components/ui/index.tsx` 的 MASCOT_DELAY 注释）。
 */
@Composable
fun DelayedFadeIn(delayMillis: Long = 600, content: @Composable BoxScope.() -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis)
        anim.animateTo(1f, animationSpec = tween(durationMillis = 300, easing = EaseOutQuint))
    }
    Box(modifier = Modifier.graphicsLayer { alpha = anim.value }, content = content)
}

/**
 * Web `.anim-content-in`：骨架 → 真实内容（**同一次路由内**的整棵子树替换）。
 *
 * 与 [RiseIn] 的区别是**不做位移**，只淡入 220ms。理由与 Web 写在
 * `index.css` 里的完全一致：大容器（详情页整块、继续阅读整条横滑）
 * 上做 8dp 位移会被读成「内容先低一点再对齐」，也就是错位；
 * 位移只留给卡片级入场 —— 元素小、有错峰，观感才是「浮现」。
 */
@Composable
fun ContentFadeIn(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, animationSpec = tween(durationMillis = 220, easing = EaseOutQuint))
    }
    Box(
        modifier = modifier.graphicsLayer {
            // 同上：只重绘，不重组。这条用在详情页整块与继续阅读横条上，
            // 组合阶段读值等于把整块大容器拖去每帧重组
            alpha = anim.value
        },
        content = content,
    )
}

/**
 * Web `.anim-icon-bump`：图标「弹一下」（收藏成功后的反馈）。
 *
 * 只动 scale、不做透明度 —— 从 0 淡入在**已经存在**的图标上播出来是「闪一下」，
 * 不是「弹一下」。自带过冲：1 → 1.32 → 0.94 → 1，320ms。
 *
 * [trigger] 是「重播开关」：CSS 那边靠摘类名再挂回去（`onAnimationEnd` 里 setFavBump(false)），
 * Compose 里用 LaunchedEffect 的 key，值一变就重播。**首次挂载不播**
 * （图标本来就是静置态，一进详情页就抖一下很怪）。
 */
@Composable
fun Modifier.iconBump(trigger: Any?): Modifier {
    val scale = remember { Animatable(1f) }
    val first = remember { arrayOf(true) }
    LaunchedEffect(trigger) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        scale.animateTo(
            1f,
            animationSpec = keyframes {
                durationMillis = 320
                1f at 0
                1.32f at 112 using EaseOutQuint
                0.94f at 224 using EaseOutQuint
                1f at 320 using EaseOutQuint
            },
        )
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Web `animate-spin`（Tailwind：`spin 1s linear infinite`）。
 * 用于「请求中」的图标位（收藏的爱心、详情页的重扫）。
 *
 * 线性、1 秒一圈：转圈是**状态指示**而不是装饰，减速或缓动会让人以为它在加载完成。
 */
@Composable
fun Modifier.spin(): Modifier {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinAngle",
    )
    return this.graphicsLayer { rotationZ = angle }
}
