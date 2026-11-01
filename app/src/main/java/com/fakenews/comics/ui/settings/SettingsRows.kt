package com.fakenews.comics.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.ui.common.BrandSwitch
import com.fakenews.comics.ui.common.CardSurface
import com.fakenews.comics.ui.theme.AppFontFamily
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.TabularNums

/** 输入框最小高度（Web `.input` 在 coarse pointer 下 44px） */
private val INPUT_MIN_HEIGHT = 44.dp

// ---- 布局基元 ----

/**
 * 设置区块（Web 的 `<section>`）：标题在**卡片外**（12sp 半粗 + 字距 0.3，ink-500），
 * 卡片本体是 `.card`（见 [CardSurface]）。行与行之间由调用处插 [RowDivider]。
 */
@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            color = LocalInk.current.ink500,
            // Web 的 <h2> 没有任何左右内距，标题左边缘与卡片左边缘齐平
            modifier = Modifier.padding(bottom = 8.dp),
        )
        CardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}

/** 行间分隔线（Web `divide-y divide-ink-800`），从卡片左边缘到右边缘。 */
@Composable
internal fun RowDivider() {
    HorizontalDivider(color = LocalInk.current.ink800, thickness = 1.dp)
}

/**
 * 设置行（Web `Row` + `block` 在手机端的形态）：
 * 标签 14sp + 提示 11sp ink-500 在上，控件独占一行在下。内距 `px-4 py-3`（16/12dp）。
 */
@Composable
internal fun SettingRow(
    label: String,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, fontSize = 14.sp, color = ink.ink100)
        if (hint != null) {
            Text(hint, fontSize = 11.sp, color = ink.ink500)
        }
        Spacer(Modifier.height(8.dp))
        control()
    }
}

/**
 * 非 block 的设置行（Web `Row` 不带 `block`）：标签/提示在左，控件贴右缘，
 * 同一行内垂直居中。色板行用它 —— 四个圆点独占一整行会显得很空。
 *
 * 色板间距为 0：AccentSwatch 的触摸面已经是 44dp（Web 是 32dp 布局 + `.touch-hit`
 * 伪元素把命中区撑到 44），再叠 10dp 的 gap 会让圆点之间松得不像一组。
 */
@Composable
internal fun InlineSettingRow(
    label: String,
    hint: String? = null,
    control: @Composable RowScope.() -> Unit,
) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).widthIn(min = 128.dp)) {
            Text(label, fontSize = 14.sp, color = ink.ink100)
            if (hint != null) {
                Text(hint, fontSize = 11.sp, color = ink.ink500)
            }
        }
        Spacer(Modifier.width(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = control,
        )
    }
}

/** 开关行：标签左 + 开关右（Web 的 Toggle 行不带 `block`，控件留在右侧）。 */
@Composable
internal fun ToggleRow(
    label: String,
    hint: String? = null,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = ink.ink100)
            if (hint != null) {
                Text(hint, fontSize = 11.sp, color = ink.ink500)
            }
        }
        Spacer(Modifier.width(16.dp))
        BrandSwitch(checked = on, onChange = onChange, enabled = enabled, label = label)
    }
}

/**
 * 表单输入框（对齐 Web `.input`）：`rounded-xl` 12dp、`bg-ink-900`、
 * `px-3 py-2` 12/8dp、`ring-1 ring-ink-800`、min-height 44dp、14sp。
 *
 * 不用 M3 的 `OutlinedTextField` —— 它的浮动标签会把占位文字变成「框上的一行小字」，
 * 与 Web 那套「框内占位」不是一个东西（H6 包已在编辑信息面板里改过同一个问题）。
 */
@Composable
internal fun FormInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    val ink = LocalInk.current
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = ink.ink100, fontFamily = AppFontFamily),
        cursorBrush = SolidColor(LocalBrandColors.current.brand500),
        visualTransformation = if (password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = modifier,
        decorationBox = { inner ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(INPUT_MIN_HEIGHT)
                    .clip(shape)
                    .background(ink.ink900)
                    .border(1.dp, ink.ink800, shape)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 14.sp, color = ink.ink500, maxLines = 1)
                }
                inner()
            }
        },
    )
}

/**
 * 信息行（逐值对齐 Web `settings-controls.tsx` 的 `Row`，关于区用的是非 block 非 uniform 那档）。
 *
 * 三段结构，与 Web 逐项对应：
 *   - 左列 `min-w-[8rem] flex-1`：14sp 标签 + 11sp 提示（长路径在列内折行）；
 *   - 中间 `gap-x-4` 的 16dp；
 *   - 右列 `shrink-0 min-h-[1.25rem] justify-end`：11sp `tabular-nums` 的状态值。
 *
 * 早先是「标签在左、值在右、提示挂在值下面」—— 提示跑到了右列，和 Web 正好反过来；
 * 于是「数据目录」在 Web 上是「路径在左、4.3 MB 在右」，Android 却成了「路径在右、
 * 4.3 MB 挂在它下面」，两行的信息位置对不上。
 */
@Composable
internal fun InfoRow(
    label: String,
    hint: String? = null,
    trailing: String? = null,
    trailingColor: Color? = null,
) {
    val ink = LocalInk.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // min-w-[8rem]：标签列至少 128dp，短标签的行右侧状态值才不会忽左忽右
        Column(Modifier.weight(1f).widthIn(min = 128.dp)) {
            Text(label, fontSize = 14.sp, color = ink.ink300, maxLines = 1)
            hint?.let {
                Text(it, fontSize = 11.sp, color = ink.ink500)
            }
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.heightIn(min = 20.dp), contentAlignment = Alignment.CenterEnd) {
            trailing?.let {
                Text(
                    it,
                    // Web `tabular-nums`：纯数字的状态值（端口 / 大小 / 已运行时长）要等宽，
                    // 否则「已运行 10 分」这种会随数字变化左右跳
                    style = TabularNums,
                    fontSize = 11.sp,
                    color = trailingColor ?: ink.ink400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
