package com.fakenews.comics.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 当前可用的提示中心（实现见 [AppToast]）。
 *
 * 用 static 而不是动态的：宿主一屏只换一次（主框架 / 详情页 / 阅读器各自 remember
 * 一个），没有「同一屏里换宿主」的场景，没必要为此建立订阅关系。
 */
val LocalToast = staticCompositionLocalOf<ToastCenter?> { null }

/** Web 的错误态固定用 Tailwind red-500，不随主题 error token 变（见 [ErrorView]）。 */
private val ErrorRed = Color(0xFFEF4444)

/**
 * 整块等待（对齐 Web `Loading`）：吉祥物 **延迟**才淡入 + 转圈 + 可选一行字。
 *
 * 为什么要延迟：等得短的时候她出现即消失，读起来是噪点而不是提示。
 * 但延迟值不能一刀切——它取决于**这一屏能活多久**，而各区段差别很大：
 *   - 内容区（漫画库/设置，默认 600ms）：路由内等待常常只有几十毫秒 → 沿用 Web 的 600ms
 *   - 首屏 boot（BootScreen）：2026-10-28 模拟器实测 436–486ms（health+authStatus 并行后
 *     仍有 1×RTT + 解析开销），600ms 门槛在那儿**一次都不会出现**，所以单独传 120ms：
 *     既能在实测那档停留 300ms+，又不会在更快的真机上闪一下。
 * 占位常驻、只淡入 —— 到点凭空插一个元素会把下面的转圈顶下去 96dp，读起来像一次抖动。
 */
@Composable
fun LoadingView(label: String? = null, modifier: Modifier = Modifier, delayMs: Long = 600) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DelayedFadeIn(delayMs) { Mascot(MascotKind.LOADING, size = 96.dp) }
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
            if (label != null) {
                // Web Loading：text-sm text-ink-400
                Text(label, fontSize = 14.sp, color = LocalInk.current.ink400)
            }
        }
    }
}

/**
 * 请求失败态（对齐 Web `ErrorState`）：红色薄底卡片 + 摊手吉祥物 + 原始 message + 重试。
 *
 * 与空态必须分开：把「请求失败」画成「还没有内容」时，NAS 关机、后端超时看起来都像
 * 「你真的没有书」，用户既不知道发生了什么也没有重试入口。
 */
@Composable
fun ErrorView(
    title: String,
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    /** 同 [EmptyView]：Web 的错误块是内容高度，只有整页替换时才等同居中。 */
    fillHeight: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    RiseIn {
        Column(
            modifier = modifier
                .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(16.dp)
                .clip(shape)
                // Web 这里固定是 red-500（#ef4444）的 6% 底 + 25% 描边，
                // 不跟主题的 error token 走 —— 深色主题的 error 是浅粉红（red-300），
                // 摊在整块背景上会偏「淡紫」，和 Web 的警示红不是一个色相。
                .background(ErrorRed.copy(alpha = 0.06f), shape)
                .border(1.dp, ErrorRed.copy(alpha = 0.25f), shape)
                .padding(horizontal = 20.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mascot(MascotKind.LOAD_FAILED, size = 112.dp)
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = LocalInk.current.ink300,
                textAlign = TextAlign.Center,
            )
            message?.takeIf { it.isNotBlank() }?.let {
                // Web：text-xs leading-relaxed text-ink-400（12sp / 行高放宽到 1.6）
                Text(
                    it,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = LocalInk.current.ink400,
                    textAlign = TextAlign.Center,
                )
            }
            if (hint != null) {
                // Web：text-[11px] text-ink-400/90
                Text(
                    hint,
                    fontSize = 11.sp,
                    color = LocalInk.current.ink400.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
            BrandButton(text = "重试", onClick = onRetry)
        }
    }
}

/**
 * 空态（对齐 Web `EmptyState`）：吉祥物插画 + 标题 + 提示 + 可选行动按钮。
 * [mascot] 默认「空」；搜索无结果传 [MascotKind.SEARCH]，漫画被删传 [MascotKind.NOT_FOUND]。
 */
@Composable
fun EmptyView(
    title: String,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    mascot: MascotKind = MascotKind.EMPTY,
    /**
     * 按钮文字右侧加一个细线箭头。Web 只有「去挑一本 / 去添加」这类**跳转**动作带
     * `<ChevronRight size={14}/>`；「新建漫画库」「清除筛选」是就地操作，不带箭头。
     */
    actionArrow: Boolean = false,
    /**
     * 是否撑满可用高度并在其中垂直居中。Web 的 `EmptyState` 是一个**内容高度**的块
     * （`py-20`），落在页面流里就是「从当前位置往下 80dp」；只有整页替换（详情页、
     * 漫画库空库）才等同居中。搜索页这种「输入框下方固定位置」的场景要传 false，
     * 否则提示会随屏幕高度漂到半空。
     */
    fillHeight: Boolean = true,
) {
    RiseIn {
        Column(
            modifier = modifier
                .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(horizontal = 24.dp, vertical = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mascot(mascot, size = 128.dp)
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = LocalInk.current.ink300,
                textAlign = TextAlign.Center,
            )
            hint?.let {
                // Web：max-w-xs text-xs text-ink-500（限宽 320dp，避免长句铺满屏）
                Text(
                    it,
                    fontSize = 12.sp,
                    color = LocalInk.current.ink500,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
            if (actionLabel != null && onAction != null) {
                // Web 的 action 统一带 `mt-2`（Column 的 gap-3 之外再补 8dp）
                BrandButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.padding(top = 8.dp),
                    trailing = if (actionArrow) {
                        { ChevronGlyph(left = false, size = 14.dp, color = Color.White) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
