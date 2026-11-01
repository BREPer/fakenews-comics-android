package com.fakenews.comics.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.LocalIsDark

/**
 * 底部弹出层（对齐 Web `components/ui/index.tsx` 的 `Sheet`）。
 *
 * 章节抽屉 / 阅读设置 / 编辑信息三处共用同一件。逐项对照 Web：
 *   - 面板材质 `.panel-surface` = `bg-ink-900/75` + `ring-1 ring-white/5`（外加 backdrop-blur，
 *     Compose 无法对背景做模糊，这里用 75% 不透明度透出背后内容来近似）；
 *   - 顶部圆角 `rounded-t-3xl` = **24dp**（M3 默认 28dp，肉眼能看出圆得更狠）；
 *   - 把手 `.sheet-grabber` = 40×4dp、`ink-700/70`、距顶 8dp（M3 默认 32×4 且无颜色表达）；
 *   - 标题栏 `px-4 py-3` + 文字 14sp 半粗 + 底部 `border-ink-800/70` 分隔线；
 *   - 右上角关闭按钮：Web 是 `X size=16` + `p-1.5` 圆角 8 的图标按钮（命中区靠 `.touch-hit`
 *     补到 44）。这里给 40dp 的点击盒，视觉仍是 16dp 的 X —— 之前完全**没有关闭按钮**，
 *     只能靠下滑或点遮罩，和 Web 不一致。
 *
 * [headerRight] 对应 Web 的 `headerRight`（如章节抽屉的「列表 / 四格」切换），排在关闭左边。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    headerRight: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = LocalInk.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        // `.panel-surface` 是 `bg-ink-900/75` + `backdrop-blur-xl`。**75% 这个值依赖模糊**：
        // Web 有 backdrop-blur，透下去的内容是糊的；Compose 没有实时背景模糊，
        // 75% 会让背后清晰可辨（实测标题下面能直接读到详情页的封面与按钮），观感是「脏」。
        // 这里按主题取一个「看得出来是浮层、但内容不透字」的值，颜色本身仍是 ink-900。
        containerColor = ink.ink900.copy(alpha = if (LocalIsDark.current) 0.94f else 0.96f),
        contentColor = ink.ink100,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        // 遮罩 `.scrim` = black/55（M3 默认 32% 太浅）
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { SheetGrabber() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 左 16dp（px-4）、右 4dp：关闭按钮自己占 40dp 的点击盒，X 视觉右缘才落在 16dp
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 14.sp, // Web text-sm
                fontWeight = FontWeight.SemiBold,
                color = ink.ink100,
            )
            Spacer(Modifier.weight(1f))
            headerRight?.invoke()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pressClickable(onClickLabel = "关闭") { onDismiss() }
                    .semantics { contentDescription = "关闭" },
                contentAlignment = Alignment.Center,
            ) {
                // Web 的 X 是 lucide 16px；Icons.Filled.Close 缩到 16 会糊，用自绘字形
                CloseGlyph(size = 16.dp, color = ink.ink400)
            }
        }
        HorizontalDivider(color = ink.ink800.copy(alpha = 0.70f), thickness = 1.dp)
        content()
    }
}

/** `.sheet-grabber`：`mx-auto mt-2 h-1 w-10 rounded-full bg-ink-700/70`。 */
@Composable
private fun SheetGrabber() {
    val ink = LocalInk.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(ink.ink700.copy(alpha = 0.70f)),
        )
    }
}
