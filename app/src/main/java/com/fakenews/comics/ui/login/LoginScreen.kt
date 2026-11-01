package com.fakenews.comics.ui.login

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakenews.comics.ui.common.FormField
import com.fakenews.comics.ui.common.FormSubmit
import com.fakenews.comics.ui.common.Mascot
import com.fakenews.comics.ui.common.MascotKind
import com.fakenews.comics.ui.common.ToastErrorRed
import com.fakenews.comics.ui.theme.LocalInk

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onChangeServer: () -> Unit,
    vm: LoginViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.loggedIn.collect { onLoggedIn() }
    }

    val ink = LocalInk.current
    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            // Web：`min-h-dvh bg-ink-950 px-4` 全屏居中
            .fillMaxSize()
            .background(ink.ink950)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        /*
         * Web 把整块表单包在一张卡里：`max-w-sm rounded-2xl border-ink-800 bg-ink-900 p-6
         * shadow-2xl`。之前在 Android 上是「直接铺在页面背景上」—— 没有卡片边界，
         * 输入框和背景糊成一片，和 Web 不是同一个观感。
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 384.dp) // Web max-w-sm
                .clip(cardShape)
                .background(ink.ink900)
                .border(1.dp, ink.ink800, cardShape)
                .padding(24.dp), // Web p-6
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 登录页也请吉祥物出场（Web Login.tsx 的 Mascot kind="login"）
            Mascot(MascotKind.LOGIN, size = 112.dp) // Web h-28
            Spacer(Modifier.height(8.dp)) // gap-2
            // Web：`text-base font-semibold text-ink-300`
            Text(
                "登录肥牛漫画",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink.ink300,
            )
            Spacer(Modifier.height(4.dp))
            // Web：`text-xs text-ink-500`「这台设备已开启登录保护」
            Text(
                "这台设备已开启登录保护",
                fontSize = 12.sp,
                color = ink.ink500,
            )
            Spacer(Modifier.height(24.dp)) // Web 头部 mb-6

            // Web 的输入框是「标签在上 + `.input`（14sp、12dp 圆角、ink-900 底、
            // 1dp ink-800 环、聚焦 2dp 品牌环）」，见 common/FormControls.kt
            FormField(
                label = "用户名",
                value = state.username,
                onValueChange = vm::onUsernameChange,
            )
            Spacer(Modifier.height(12.dp)) // Web 第一个 label 的 mb-3
            FormField(
                label = "密码",
                value = state.password,
                onValueChange = vm::onPasswordChange,
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeAction = vm::login,
            )
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                // Web：`text-xs text-red-400`（12px），裸 Text 会落到 bodyLarge 16sp
                Text(it, fontSize = 12.sp, color = ToastErrorRed)
            }
            Spacer(Modifier.height(16.dp)) // Web 密码块的 mb-4
            FormSubmit(
                text = "登录",
                busyText = "登录中…",
                busy = state.submitting,
                onClick = vm::login,
            )
            TextButton(onClick = onChangeServer, modifier = Modifier.padding(top = 4.dp)) {
                // Web 没有这一项（浏览器里换服务器只需改地址栏）；Android 必须留入口
                Text("更换服务器", fontSize = 12.sp, color = ink.ink500)
            }
        }
    }
}
