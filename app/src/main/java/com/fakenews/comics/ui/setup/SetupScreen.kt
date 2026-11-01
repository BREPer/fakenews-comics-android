package com.fakenews.comics.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakenews.comics.ui.common.Mascot
import com.fakenews.comics.ui.common.MascotKind
import com.fakenews.comics.ui.common.FormField
import com.fakenews.comics.ui.common.FormSubmit
import com.fakenews.comics.ui.common.ToastErrorRed
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 首次启动的「连接服务器」页（Web 没有这一屏：浏览器里地址栏就是服务器地址）。
 *
 * 版式照 [com.fakenews.comics.ui.login.LoginScreen]：同一张 `max-w-sm` 卡片、同一个上置标签
 * 输入框、同一颗实心品牌按钮。之前这里用 M3 的 OutlinedTextField + Button，新装用户看到的
 * 第一屏和紧接着的登录页是两种设计语言 —— 而这正是「这个 App 长什么样」的第一印象。
 */
@Composable
fun SetupScreen(
    onNeedLogin: () -> Unit,
    onReady: () -> Unit,
    vm: SetupViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.target.collect { target ->
            when (target) {
                SetupTarget.Login -> onNeedLogin()
                SetupTarget.Shelf -> onReady()
            }
        }
    }

    val ink = LocalInk.current
    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ink.ink950)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 384.dp) // 与登录卡同宽（Web max-w-sm）
                .clip(cardShape)
                .background(ink.ink900)
                .border(1.dp, ink.ink800, cardShape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 连接页的吉祥物（抱书蹦起来）：与登录页/空态同一套线稿 + tint，
            // 深浅主题都跟着 ink300 翻转。
            // 比登录页的 112dp 再大一档：这张是一整幅「人 + 摊开的书」的场景，
            // 同尺寸下人物只占一半，按 112dp 放会显得又小又空。
            Mascot(MascotKind.SETUP, size = 136.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "肥牛漫画",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink.ink300,
            )
            Spacer(Modifier.height(4.dp))
            Text("连接你的 fnOS 服务器", fontSize = 12.sp, color = ink.ink500)
            Spacer(Modifier.height(24.dp))

            FormField(
                label = "服务器地址",
                value = state.url,
                onValueChange = vm::onUrlChange,
                placeholder = "http://192.168.1.10:13800",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                onImeAction = vm::connect,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "局域网可直接用 http；如通过反向代理暴露到外网，请使用 https。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = ink.ink500,
            )
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontSize = 12.sp, color = ToastErrorRed)
            }
            Spacer(Modifier.height(16.dp))
            FormSubmit(
                text = "连接",
                busyText = "连接中…",
                busy = state.testing,
                enabled = state.url.isNotBlank(),
                onClick = vm::connect,
            )
        }
    }
}
