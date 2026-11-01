package com.fakenews.comics.ui.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.data.model.LibraryBrowseDto
import com.fakenews.comics.ui.common.AppSheet
import com.fakenews.comics.ui.common.BrandButton
import com.fakenews.comics.ui.common.FolderGlyph
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.accentTextColor

/**
 * 目录点选器（ Web`components/library/DirPicker.tsx`）。
 *
 * 存在的理由写在服务端 `GET /api/libraries/browse` 的注释里：路径手输是错的温床 ——
 * 容器内恒定只见 `/comics`，用户却拿飞牛的宿主机路径（`/vol1/...`）来填，扫完书架是空的
 * 还要排查半天。点选从挂载根起步，给出来的路径天然可访问。
 *
 * [levels] 是被下钻过的层级栈（第 0 层是根），每一层的数据已经在内存里，
 * 所以面包屑「回跳」只做截尾、不再发请求；只有往下钻才问服务端要新的一层。
 */
@Composable
fun DirPickerSheet(
    levels: List<LibraryBrowseDto>,
    loading: Boolean,
    error: String?,
    checked: List<String>,
    onDismiss: () -> Unit,
    onDrill: (path: String) -> Unit,
    onCrumb: (index: Int) -> Unit,
    onToggle: (path: String) -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val cur = levels.lastOrNull()

    AppSheet(title = "选择目录", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 面包屑：最后一截是当前层（不可点），点前面的任意一截断到那层
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                levels.forEachIndexed { i, level ->
                    if (i > 0) {
                        Text("/", fontSize = 12.sp, color = ink.ink600)
                        Spacer(Modifier.width(2.dp))
                    }
                    val last = i == levels.lastIndex
                    Text(
                        if (i == 0) "漫画根目录" else level.path.substringAfterLast('/'),
                        fontSize = 12.sp,
                        fontWeight = if (last) FontWeight.Medium else FontWeight.Normal,
                        color = if (last) ink.ink200 else ink.ink500,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (last) {
                                    Modifier.pressClickable(enabled = false) {}
                                } else {
                                    Modifier.pressClickable { onCrumb(i) }
                                },
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }

            if (loading && cur == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.8.dp, color = ink.ink500)
                    Spacer(Modifier.width(8.dp))
                    Text("读取中…", fontSize = 14.sp, color = ink.ink500)
                }
            }

            if (error != null) {
                // Web：`border-amber-500/40 bg-amber-500/10 text-amber-300`，
                // 并把「去 fnOS 授权」这一步直接写在提示里（这段字是排查脚本，不能省）
                AmberNote(
                    "⚠️ $error。若为 fnOS，请在「设置 → 应用 → 肥牛漫画 → 文件夹访问权限」勾选允许访问，然后重试。",
                )
            }

            if (cur != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Web：max-h-[50vh] min-h-[160px] rounded-xl bg-ink-900 p-1.5
                        .heightIn(min = 160.dp, max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ink.ink900)
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (cur.items.isEmpty()) {
                        Text(
                            "此层没有子目录了",
                            fontSize = 12.sp,
                            color = ink.ink500,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    cur.items.forEach { item ->
                        val isChecked = item.path in checked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 下钻：整行（除勾选热区外）点一下进这个目录
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .pressClickable { onDrill(item.path) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FolderGlyph(
                                    open = false,
                                    size = 16.dp,
                                    color = brand.brand500,
                                )
                                Text(
                                    item.name,
                                    fontSize = 14.sp,
                                    color = ink.ink100,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (item.leafHint) {
                                    // 「这一层本身就是一部漫画」的小标（Web 的 title 是同一句解释）
                                    Text(
                                        "单部",
                                        fontSize = 10.sp,
                                        color = ink.ink400,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ink.ink800)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            // 勾选：独立热区，避免和下钻抢点击（Web 也是把两个动作拆成两个按钮）
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .pressClickable(onClickLabel = if (isChecked) "取消勾选" else "勾选为库路径") {
                                        onToggle(item.path)
                                    }
                                    .semantics { contentDescription = if (isChecked) "已勾选 ${item.name}" else item.name },
                                contentAlignment = Alignment.Center,
                            ) {
                                FolderGlyph(
                                    open = isChecked,
                                    size = 17.dp,
                                    color = if (isChecked) accentTextColor() else ink.ink600,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("已选 ${checked.size} 个目录", fontSize = 11.sp, color = ink.ink500)
                    if (checked.isNotEmpty()) {
                        val last = checked.last()
                        Text(
                            "…/${last.substringAfterLast('/')}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ink.ink400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                BrandButton(
                    text = "确定",
                    onClick = { onConfirm(checked) },
                    enabled = checked.isNotEmpty(),
                )
            }
        }
    }
}

/** 点选器里的琥珀提示条（Web 同一份 amber 配色）。 */
@Composable
internal fun AmberNote(text: String) {
    val amber500 = Color(0xFFF59E0B)
    val amber300 = Color(0xFFFCD34D)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(amber500.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 11.sp, color = amber300)
    }
}
