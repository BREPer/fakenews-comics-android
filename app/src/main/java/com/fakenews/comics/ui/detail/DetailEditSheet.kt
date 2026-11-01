package com.fakenews.comics.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.ui.common.AppSheet
import com.fakenews.comics.ui.common.BrandButton
import com.fakenews.comics.ui.common.CloseGlyph
import com.fakenews.comics.ui.common.GhostButton
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.theme.AppFontFamily
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk

/** 编辑表单的草稿。字段都是字符串：非空判断交给 VM，这里只管收集。 */
data class EditDraft(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
)

/**
 * 编辑信息面板（对齐 Web `Detail.tsx:394-488` 的 Sheet）。
 *
 * 标签是**整份提交**：服务端按「去空、去重」后的列表覆盖，所以删掉一个 chip 就等于
 * 从首页筛选栏里撤掉它；保存会把这本书置为 meta_locked，重扫不再覆盖。
 */
@Composable
fun EditMetaSheet(
    draft: EditDraft,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (EditDraft) -> Unit,
) {
    AppSheet(title = "编辑信息", onDismiss = { if (!saving) onDismiss() }) {
        EditMetaForm(
            initial = draft,
            title = draft.title,
            saving = saving,
            onCancel = onDismiss,
            onSave = onSave,
        )
    }
}

/**
 * 表单本体（逐值对齐 Web 的编辑面板）：
 *   - 容器 `flex flex-col gap-3 p-4` → 16dp 内距、12dp 行距；
 *   - 字段标签是**输入框上方的一行 12sp `ink-400` 小字**（不是 M3 的浮动 label：
 *     Web 那里 `<label className="text-xs text-ink-400">` 与 `.input` 是两个独立元素的上下排）；
 *   - `.input` = `rounded-xl`(12dp) + `bg-ink-900` + `px-3 py-2`(12/8dp) + 14sp `ink-100`
 *     + `ring-1 ring-ink-800`，最小高度 44px；
 *   - 标签 chip = 4dp 圆角 + `bg-ink-800` + 11sp `ink-200` + 右侧 11px 关闭叉；
 *   - 添加行 = 输入框 + ghost「添加」（`px-3 ring-1 ring-ink-800`）；
 *   - 底部只有**一个全宽主按钮**「保存」——Web 没有取消按钮，取消由标题栏的 × 承担。
 *
 * [onCancel] 保留给 instrumented 测试与调用方签名兼容，UI 上不再渲染取消按钮。
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMetaForm(
    initial: EditDraft,
    title: String,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (EditDraft) -> Unit,
) {
    val ink = LocalInk.current
    var draft by remember(initial) { mutableStateOf(initial) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp), // p-4
        verticalArrangement = Arrangement.spacedBy(12.dp), // gap-3
    ) {
        FormLabel("标题")
        FormInput(
            value = draft.title,
            enabled = !saving,
            tag = "edit_标题",
        ) { draft = draft.copy(title = it) }

        FormLabel("作者")
        FormInput(
            value = draft.author,
            enabled = !saving,
            tag = "edit_作者",
        ) { draft = draft.copy(author = it) }

        FormLabel("简介")
        FormInput(
            value = draft.description,
            enabled = !saving,
            tag = "edit_简介",
            minHeight = 96.dp,
            singleLine = false,
        ) { draft = draft.copy(description = it) }

        // 分类标签：首页筛选 chips 的来源
        FormLabel("分类 / 标签")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { // gap-1.5
            if (draft.tags.isEmpty()) {
                Text("还没有分类", fontSize = 11.sp, color = ink.ink500)
            }
            draft.tags.forEach { tag ->
                TagChip(
                    text = tag,
                    enabled = !saving,
                    onRemove = { draft = draft.copy(tags = draft.tags - tag) },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TagInput(enabled = !saving, modifier = Modifier.weight(1f)) { input ->
                val incoming = input.split(',', '，', '、', ';', '；')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (incoming.isNotEmpty()) {
                    draft = draft.copy(tags = (draft.tags + incoming).distinct())
                }
            }
        }
        Text(
            "这些分类就是首页筛选栏里的标签，点 × 删除后首页就不再显示它。保存过的分类会被锁定，重扫不会覆盖。",
            fontSize = 11.sp,
            lineHeight = 18.sp, // leading-relaxed
            color = ink.ink500,
        )

        Spacer(Modifier.height(4.dp))
        BrandButton(
            text = if (saving) "保存中…" else "保存",
            onClick = { onSave(draft) },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** `<label className="text-xs text-ink-400">`：12sp、ink-400、紧贴下方输入框。 */
@Composable
private fun FormLabel(text: String) {
    Text(text, fontSize = 12.sp, color = LocalInk.current.ink400)
}

/**
 * `.input`：12dp 圆角 + `ink-900` 底 + 1px `ink-800` 描边 + 14sp `ink-100` 文字，
 * 最小 44dp 高、内距 12/8dp。用 [BasicTextField] 自绘而不是 `OutlinedTextField`：
 * 后者的浮动 label 与 56dp 高度都不是 Web 的形态（Web 的标签是外面独立的一行）。
 */
@Composable
private fun FormInput(
    value: String,
    enabled: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = TextStyle(fontSize = 14.sp, color = ink.ink100, fontFamily = AppFontFamily),
        cursorBrush = SolidColor(brand.brand500),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(ink.ink900, shape)
            .border(1.dp, ink.ink800, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(tag),
    )
}

/** 标签 chip：`rounded bg-ink-800 py-1 pl-2 pr-1 text-[11px] text-ink-200`。 */
@Composable
private fun TagChip(text: String, enabled: Boolean, onRemove: () -> Unit) {
    val ink = LocalInk.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ink.ink800)
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 11.sp, color = ink.ink200)
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .pressClickable(enabled = enabled, pressedScale = 0.9f) { onRemove() }
                .semantics { contentDescription = "删除分类「$text」" },
            contentAlignment = Alignment.Center,
        ) {
            CloseGlyph(size = 11.dp, color = ink.ink500)
        }
    }
}

/** 添加行：输入框（flex-1）+ ghost「添加」（`px-3 ring-1 ring-ink-800`，高度对齐 44dp）。 */
@Composable
private fun TagInput(enabled: Boolean, modifier: Modifier = Modifier, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormInput(
            value = text,
            enabled = enabled,
            tag = "edit_tagInput",
            modifier = Modifier.weight(1f),
            onValueChange = { text = it },
        )
        GhostButton(
            text = "添加",
            onClick = {
                onAdd(text)
                text = ""
            },
            enabled = enabled && text.isNotBlank(),
            modifier = Modifier.testTag("edit_tagAdd"),
        )
    }
}
