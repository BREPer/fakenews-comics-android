package com.fakenews.comics.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.ui.theme.AppFontFamily
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 卡片内表单的输入框（对齐 Web `.input` + 上置标签）：登录页与「连接服务器」页共用。
 *
 * 标签 `mb-1 text-xs text-ink-400`；输入区 `rounded-xl bg-ink-900 px-3 py-2 text-sm`，
 * 未聚焦 1dp ink-800 环、聚焦 2dp `brand-600/80`，150ms 过渡（与 `transition-[box-shadow]` 同源）。
 * 触摸端 `.input` 有 `min-height: 44px`，这里同样保底 44dp。
 *
 * 不用 M3 的 OutlinedTextField：浮动标签会占掉输入区高度、字号默认 16sp、聚焦环走
 * colorScheme.primary —— 与 Web 那套「标签在框外、框内只有占位」不是同一个东西。
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        animationSpec = tween(150),
        label = "formFieldRingWidth",
    )
    val ringColor by animateColorAsState(
        targetValue = if (focused) brand.brand600.copy(alpha = 0.8f) else ink.ink800,
        animationSpec = tween(150),
        label = "formFieldRingColor",
    )
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = ink.ink400)
        Spacer(Modifier.height(4.dp)) // Web mb-1
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(ink.ink900)
                .border(ringWidth, ringColor, shape)
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            textStyle = TextStyle(fontSize = 14.sp, color = ink.ink100, fontFamily = AppFontFamily),
            singleLine = true,
            cursorBrush = SolidColor(brand.brand500),
            visualTransformation =
                if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType ?: if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() },
                onGo = { onImeAction() },
            ),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            fontSize = 14.sp,
                            color = ink.ink500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * 卡片内的提交按钮（Web：`w-full rounded-lg bg-brand-600 py-2 text-sm font-medium text-white
 * disabled:opacity-60`）。不是 `.btn`，所以没有渐变与顶部高光；忙碌时只换文案、不转圈。
 *
 * 高 44dp 而不是 Web 的 36px：触摸端 `.btn` 靠 `.touch-hit` 把命中区补到 44，这里直接
 * 让按钮本体够高。
 */
@Composable
fun FormSubmit(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busyText: String = text,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val brand = LocalBrandColors.current
    // 禁用/忙碌时把透明度压到 60%（Web `disabled:opacity-60`）。这里用**颜色自带的 alpha**
    // 而不是 `Modifier.alpha`：后者会在可用/禁用切换时增删一层 graphicsLayer，实测在模拟器上
    // 移除图层后那块区域不再重绘（按钮变成一片白，只剩白字看不见）。
    val active = enabled && !busy
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp)) // Web rounded-lg
            .background(brand.brand600.copy(alpha = if (active) 1f else 0.6f))
            .pressClickable(
                enabled = active,
                pressedScale = 0.97f,
                durationMillis = 150,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (busy) busyText else text,
            // Web 的 opacity-60 是整颗按钮一起淡：文字也要跟着淡，只压底色会显得比 Web 更"亮"
            color = Color.White.copy(alpha = if (active) 1f else 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
