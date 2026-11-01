package com.fakenews.comics.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 轻提示（Toast）—— 对齐 Web `components/ui/toast.tsx`。
 *
 * 为什么不用 M3 的 Snackbar：两者差的不是样式，是**信息密度**。
 *   - Web 每条带 kind 图标（成功打勾 / 失败圆圈叹号 / 普通信息），扫一眼就知道是好是坏；
 *     Snackbar 只有一行字，「已保存」和「保存失败」长得一模一样。
 *   - Web 有独立关闭按钮，且**最多并存 3 条**；Snackbar 单条串行，第二条要等第一条
 *     自己走完（连点两次收藏，第二条提示要 4 秒后才出现）。
 *   - Web 的时长分档（信息/成功 2.4s、失败 4.6s、可撤销 6s）；Snackbar 只有 4s/10s 两档，
 *     失败提示和成功提示同寿。
 *
 * 渲染与状态分离：[ToastCenter] 只管队列与计时（任何协程里都能调），[ToastHost] 只管画。
 * 与 Web 的模块级 store 同构 —— 提示能从一个「不在组件树里」的地方发出。
 */

/** 提示性质，决定图标与配色（Web 的 `ToastKind`）。 */
enum class ToastKind { INFO, SUCCESS, ERROR }

/**
 * VM → 界面的单条提示。
 *
 * 之所以带 kind 而不是继续传裸 String：Web 那边 `toast.success()` 与 `toast.error()`
 * 是两个函数，图标、配色、停留时长都不同。裸 String 只能让「已保存」和「保存失败」
 * 长得一模一样，那等于把信息量让给用户去猜。
 */
data class UiMessage(val text: String, val kind: ToastKind = ToastKind.INFO) {
    companion object {
        fun ok(text: String) = UiMessage(text, ToastKind.SUCCESS)
        fun err(text: String) = UiMessage(text, ToastKind.ERROR)
        fun info(text: String) = UiMessage(text, ToastKind.INFO)
    }
}

/** 同时最多 3 条：再多会盖住内容，也没有阅读价值（Web `MAX = 3`）。 */
private const val MAX_TOASTS = 3

/** 退场留量：淡出下沉 160ms + 20ms 余量再摘（Web `LEAVE_MS = 180`）。 */
private const val LEAVE_MS = 180L

/** 停留时长：错误留久一点（要读得完），带撤销的再久一点（要够手速）。 */
private fun ttlOf(kind: ToastKind, hasAction: Boolean): Long = when {
    hasAction -> 6000
    kind == ToastKind.ERROR -> 4600
    else -> 2400
}

internal data class ToastItem(
    val id: Int,
    val text: String,
    val kind: ToastKind,
    val actionLabel: String? = null,
    /** 正在播退场动画：还在列表里，但已经不可点 */
    val leaving: Boolean = false,
)

/**
 * 提示队列。
 *
 * [show] 是挂起函数，返回值语义与 `SnackbarHostState.showSnackbar` 一致：
 * true 表示用户点了附带操作（撤销），false 表示超时 / 被挤掉 / 手动关闭。
 * 撤销流程依赖它（`ui/shelf/ShelfScreen.kt` 的 showUndoSnackbar）。
 *
 * 调用姿势：`collect` 里要**每次新起一个协程**去 show，否则第二条提示会等第一条
 * 走完 2.4 秒才出现 —— Web 那边是多条并存的。
 */
@Stable
class ToastCenter {
    internal val items = mutableStateListOf<ToastItem>()
    private val pending = mutableMapOf<Int, CompletableDeferred<Boolean>>()
    private var seq = 0

    suspend fun show(
        text: String,
        kind: ToastKind = ToastKind.INFO,
        actionLabel: String? = null,
        durationMillis: Long = ttlOf(kind, actionLabel != null),
    ): Boolean {
        val id = ++seq
        val done = CompletableDeferred<Boolean>()
        pending[id] = done
        // 挤出最旧的：必须给它一个结果，否则那条协程永远挂着（撤销窗口的删除就落不了定）
        while (items.size >= MAX_TOASTS) {
            val oldest = items.removeAt(0)
            pending.remove(oldest.id)?.complete(false)
        }
        items.add(ToastItem(id = id, text = text, kind = kind, actionLabel = actionLabel))
        return try {
            withTimeout(durationMillis) { done.await() }
        } catch (_: TimeoutCancellationException) {
            false
        } finally {
            // 无论超时、点操作还是被挤掉，都要把退场动画走完再摘
            withContext(NonCancellable) {
                markLeaving(id)
                delay(LEAVE_MS)
                items.removeAll { it.id == id }
                pending.remove(id)
            }
        }
    }

    /** 关掉一条：点了「关闭」传 false，点了附带操作传 true。 */
    fun dismiss(id: Int, performedAction: Boolean = false) {
        pending[id]?.complete(performedAction)
    }

    private fun markLeaving(id: Int) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(leaving = true)
    }
}

/**
 * 渲染层：底部居中一列卡片（Web 是 `fixed inset-x-0 bottom-0` 且撑到安全区之上）。
 *
 * 插在调用方给的 [modifier] 位置 —— 主页挂在 bottomBar 之上、详情页挂在屏幕底部、
 * 阅读器挂在工具栏之上，三处间距不同，由调用方决定。
 */
@Composable
fun ToastHost(
    center: ToastCenter,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 最多 3 条，不用 LazyColumn：lazy 的 item 复用会让退场动画对不上号
        center.items.forEach { item ->
            key(item.id) {
                ToastRow(
                    item = item,
                    hazeState = hazeState,
                    onAction = { center.dismiss(item.id, performedAction = true) },
                    onClose = { center.dismiss(item.id) },
                )
            }
        }
    }
}

@Composable
private fun ToastRow(
    item: ToastItem,
    hazeState: HazeState?,
    onAction: () -> Unit,
    onClose: () -> Unit,
) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val density = LocalDensity.current
    val inY = with(density) { 10.dp.roundToPx() }
    val outY = with(density) { 6.dp.roundToPx() }
    val shape = RoundedCornerShape(12.dp)

    // 进场 200 / 退场 160：提示该「被看到」时慢一点，「该走」时干脆一点（Web 同值）
    AnimatedVisibility(
        visible = !item.leaving,
        enter = fadeIn(tween(200, easing = EaseOutQuint)) +
            scaleIn(initialScale = 0.97f, animationSpec = tween(200, easing = EaseOutQuint)) +
            slideInVertically(tween(200, easing = EaseOutQuint)) { inY } +
            expandVertically(tween(200, easing = EaseOutQuint)),
        exit = fadeOut(tween(160, easing = EaseOutQuint)) +
            scaleOut(targetScale = 0.97f, animationSpec = tween(160, easing = EaseOutQuint)) +
            slideOutVertically(tween(160, easing = EaseOutQuint)) { outY } +
            // 收高度：否则「消失」只是淡出，下面的条目会瞬间往上跳一格
            shrinkVertically(tween(150, easing = EaseOutQuint)),
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 340.dp)
                // Web 是 bg-ink-900/90 + backdrop-blur-xl。早先没有采样源，只能把不透明度
                // 提到 0.94 冒充玻璃；现在统一走 glass()：真模糊 + 0.90 的 tint。
                // 没有采样源（详情页以外的一些宿主）时 glass() 自己退回近不透明底。
                .glass(hazeState, shape, tint = ink.ink900, alpha = 0.90f, blurRadius = 16.dp)
                .border(1.dp, ink.ink800.copy(alpha = 0.70f), shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToastIcon(item.kind)
            Spacer(Modifier.width(10.dp))
            Text(
                text = item.text,
                fontSize = 12.sp,
                color = ink.ink200,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
            )
            if (item.actionLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.actionLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = brand.brand500,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .pressClickable(onClickLabel = item.actionLabel, onClick = onAction)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            // 关闭按钮：Web 每条都有。这是最容易被忽略的一处差异 ——
            // 用户读完一条却要干等 2.4 秒它才消失
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .pressClickable(onClickLabel = "关闭提示", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                CloseGlyph(size = 12.dp, color = ink.ink500)            }
        }
    }
}

@Composable
private fun ToastIcon(kind: ToastKind) {
    val brand = LocalBrandColors.current
    val ink = LocalInk.current
    when (kind) {
        ToastKind.SUCCESS -> Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = brand.brand500,
            modifier = Modifier.size(15.dp),
        )
        ToastKind.INFO -> Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = ink.ink400,
            modifier = Modifier.size(15.dp),
        )
        // 核心图标集里没有 AlertCircle（圆圈叹号，在 extended）；Warning 是实心三角，
        // 与 Web 的 lucide 线稿不是一套语言，所以自绘一圆环 + 一竖一点
        ToastKind.ERROR -> AlertCircleGlyph(size = 15.dp, color = ToastErrorRed)
    }
}

/**
 * Web `text-red-400` = #F87171（Tailwind 红 400，深底上够亮又不刺眼）。
 * 设置页「数据目录不可写」用的是同一个值（Web `Settings.tsx:590`），所以不设成 private。
 */
val ToastErrorRed = Color(0xFFF87171)
