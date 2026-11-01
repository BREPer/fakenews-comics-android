package com.fakenews.comics.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fakenews.comics.R
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 状态吉祥物（品牌牛角娘）——对齐 Web `components/ui/index.tsx` 的 `Mascot`。
 *
 * 素材与 Web 同源：`app/web/public/status/status-*.webp`（512×512 线稿，透明底），
 * 这里复制为 `res/drawable-nodpi/status_*.webp`（nodpi：不做密度缩放，渲染时按 dp 缩到目标尺寸）。
 *
 * 上色方式与 Web 完全一致：Web 用 `mask: url(...)` + `background-color: rgb(--ink-300)`
 * 把线稿整体染成 ink-300；Compose 没有 mask，但用 `ColorFilter.tint(ink300)` 等价
 * ——原图本来就是单色线稿 + alpha，tint 只改颜色不改透明度，所以浅色主题得到深灰线、
 * 深色主题得到浅灰线，插画跟着主题走。
 */
enum class MascotKind(val res: Int) {
    NOT_FOUND(R.drawable.status_404),
    EMPTY(R.drawable.status_empty),
    SEARCH(R.drawable.status_search_empty),
    LOAD_FAILED(R.drawable.status_load_failed),
    LOGIN(R.drawable.status_login),
    LOADING(R.drawable.status_loading),

    /** 连接服务器页（首次配置）：抱着书蹦起来的那张，比应用图标更能说明「这是要连自己的库」 */
    SETUP(R.drawable.status_setup),
}

@Composable
fun Mascot(
    kind: MascotKind,
    size: Dp = 128.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.BottomCenter) {
        Image(
            painter = painterResource(kind.res),
            contentDescription = null,
            // 与 Web `mask ... center bottom / contain` 同位：等比缩放、贴底对齐
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            colorFilter = ColorFilter.tint(LocalInk.current.ink300),
            modifier = Modifier.size(size),
        )
    }
}
