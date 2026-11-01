package com.fakenews.comics.ui.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakenews.comics.ui.common.AppSheet
import com.fakenews.comics.ui.common.BrandButton
import com.fakenews.comics.ui.common.BrandChip
import com.fakenews.comics.ui.common.FolderGlyph
import com.fakenews.comics.ui.common.FormField
import com.fakenews.comics.ui.common.GhostButton
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 新建 / 编辑漫画库的表单（对齐 Web `pages/Libraries.tsx` 的那张 Sheet）。
 *
 * 逐项对照 Web：
 *   - 容器 `flex flex-col gap-3 p-4` → 16dp 内距、12dp 行距；
 *   - 名称 = label + `.input`（复用 [FormField]，它与这两种页面用的是同一件）；
 *   - 类型 = 两个 chip（`chip-on` / `chip-off`），不是输入框也不是分段控件；
 *   - 目录 = label + 右侧「浏览选择」 + 多行 textarea（`min-h-[120px] font-mono text-xs`）；
 *   - 底部一个全宽主按钮「保存并扫描」。
 *
 * 点选器的 Browse 状态在 [vm] 里（要发请求），所以这里直接收 VM ——
 * 相比把七八个回调逐一搬下来，读起来反而更短；UI 侧只留「面板上讲什么」。
 */
@Composable
internal fun LibraryFormSheet(
    vm: LibrariesViewModel,
    initial: LibraryDraft,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (LibraryDraft) -> Unit,
) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val state by vm.state.collectAsStateWithLifecycle()
    var draft by remember(initial) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }

    AppSheet(
        title = if (initial.id == null) "新建漫画库" else "编辑漫画库",
        onDismiss = { if (!saving) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(
                label = "名称",
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                placeholder = "例如：本机漫画",
                modifier = Modifier.fillMaxWidth(),
            )

            Text("类型", fontSize = 12.sp, color = ink.ink400)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrandChip(
                    text = "章节漫画",
                    selected = draft.type == "chapter",
                    onClick = { draft = draft.copy(type = "chapter") },
                    enabled = !saving,
                )
                BrandChip(
                    text = "单篇漫画",
                    selected = draft.type == "single",
                    onClick = { draft = draft.copy(type = "single") },
                    enabled = !saving,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("目录路径（每行一个）", fontSize = 12.sp, color = ink.ink400)
                Spacer(Modifier.weight(1f))
                GhostButton(
                    text = "浏览选择",
                    onClick = { picking = true },
                    enabled = !saving,
                    iconContent = {
                        FolderGlyph(open = true, size = 13.dp, color = ink.ink300)
                    },
                    small = true,
                )
            }
            PathsEditor(
                value = draft.paths,
                enabled = !saving,
                onValueChange = { draft = draft.copy(paths = it) },
            )
            Text(
                "推荐点「浏览选择」从挂载根逐层勾选，给出来的路径必然有效。手输的话填容器内路径：" +
                    "安装时填写的漫画根目录（宿主机路径）已被挂载到容器内的 /comics，只收录某个子目录请填 " +
                    "/comics/子目录。不要填宿主机路径（如 /vol1/...），容器内看不到，会导致书架为空。",
                fontSize = 11.sp,
                color = ink.ink500,
            )
            Text(
                "识别规则：章节型每个子文件夹是一部漫画；单篇型每个含图文件夹/压缩包各自成书。" +
                    "支持图片文件夹与 .cbz/.zip/.tar/.cbt/.rar/.cbr/.epub/.pdf。" +
                    "隐藏目录（点号开头）与样张文件会被忽略。保存后自动触发扫描。",
                fontSize = 11.sp,
                color = ink.ink500,
            )

            Spacer(Modifier.height(4.dp))
            BrandButton(
                text = if (saving) "保存中…" else "保存并扫描",
                onClick = { onSave(draft) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (picking) {
        // 每次打开都从根开始 + 预勾当前文本框里的路径（VM 里做重置，
        // 免得把上一次翻到第几层的位置带到这次编辑里）
        LaunchedEffect(Unit) {
            vm.openPicker(
                draft.paths.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList(),
            )
        }
        DirPickerSheet(
            levels = state.browseLevels,
            loading = state.browseLoading,
            error = state.browseError,
            checked = state.browseChecked,
            onDismiss = { picking = false; vm.closePicker() },
            onDrill = vm::drillPicker,
            onCrumb = vm::pickerCrumbTo,
            onToggle = vm::togglePickerPath,
            onConfirm = { paths ->
                draft = draft.copy(paths = paths.joinToString("\n"))
                picking = false
                vm.closePicker()
            },
        )
    }
}

/** Web 的路径 textarea：`min-h-[120px] font-mono text-xs`。 */
@Composable
private fun PathsEditor(value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = false,
        textStyle = TextStyle(
            fontSize = 12.sp,
            color = ink.ink100,
            fontFamily = FontFamily.Monospace,
        ),
        cursorBrush = SolidColor(brand.brand500),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(ink.ink900, shape)
            .border(1.dp, ink.ink800, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
