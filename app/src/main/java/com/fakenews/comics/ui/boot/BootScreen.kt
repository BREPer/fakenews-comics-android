package com.fakenews.comics.ui.boot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakenews.comics.R
import com.fakenews.comics.ui.common.Mascot
import com.fakenews.comics.ui.common.MascotKind
import kotlinx.coroutines.delay

/**
 * 启动海报至少停留这么久。
 *
 * boot 实测 436–486ms（health + authStatus 并行后仍有 1×RTT 加解析），也就是海报自然
 * 只会出现半秒不到 —— 再压短就成了「闪一下」，比不放还难受；620ms 能让人看清画面，
 * 又不会把冷启动拖成负担。之后由 NavHost 的 200ms 淡入接走（RouteFadeSpec）。
 */
private const val SPLASH_MIN_MS = 620L

@Composable
fun BootScreen(
    onNeedSetup: () -> Unit,
    onNeedLogin: () -> Unit,
    onReady: () -> Unit,
    vm: BootViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // 抢在「连接结果」之前先满足最短展示时间：结果来得快就等它，来得慢就直接走。
    // 用 remember 而不是 rememberSaveable —— 旋转不会重建 Activity（configChanges 已声明），
    // 真重建一次重播 620ms 也无害。
    var dwellDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_MS)
        dwellDone = true
    }

    LaunchedEffect(state, dwellDone) {
        if (!dwellDone) return@LaunchedEffect
        when (state) {
            BootUiState.NoServer -> onNeedSetup()
            BootUiState.NeedLogin -> onNeedLogin()
            BootUiState.Ready -> onReady()
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 与系统闪屏、海报底色同一个值：三者交接时不该出现色块跳变
            .background(colorResource(R.color.launch_poster_bg)),
    ) {
        when (val current = state) {
            // 失败态自己铺回主题底色：海报底色是浅色的，深色主题下把浅色文字压在上面
            // 会直接读不出来
            is BootUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .safeDrawingPadding()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 连不上服务器时也放一张吉祥物：整页只有三行文字很像系统报错，
                    // 有画面才像「我们的 App 在跟你说话」。
                    // 用 404 那张（找不到东西的姿势）—— 连不上通常就是地址写错、
                    // 服务端没起、或者不在同一个网里，语义上比「加载失败」更贴。
                    Mascot(MascotKind.NOT_FOUND, size = 136.dp)
                    Spacer(Modifier.height(16.dp))
                    // 裸 Text 在这里拿不到宿主 contentColor（本页没有 Scaffold/Surface），
                    // 不写就是 CompositionLocal 默认的纯黑 —— 深色主题下压在同色背景上。
                    Text(
                        "连接失败",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        current.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = vm::refresh) { Text("重试") }
                    TextButton(onClick = onNeedSetup) { Text("更换服务器") }
                }
            }

            // 其余状态（连接中 / 已就绪待跳转）都停在启动海报上。以前这里是
            // LoadingView（吉祥物 + 转圈），但转圈在 400ms 内根本转不满一圈，
            // 换成整幅海报既是品牌露出，也不用为一个看不见的进度条解释自己。
            else -> SplashPoster()
        }
    }
}

/**
 * 启动海报：铺满整屏，不参与 safeDrawing —— 画面要出血到状态栏与小白条后面。
 *
 * 缩放策略按屏幕比例分两支：
 *   - 竖屏（含方屏）：Crop。海报是 941×1672（比例 0.563），1080 宽的手机按高铺满后
 *     左右各裁 11%，人物完全在画面内，这点裁切换来真正的满屏；
 *   - 横屏 / 平板：Fit。同样的 Crop 在 2340×1080 上要上下各裁 37%，只剩一张脸。
 *
 * 用 painterResource 而不是 Coil：这一帧的价值就是「第一帧就有画面」。同步解码
 * （941×1672 ≈ 6MB 位图）会多吃十几毫秒，但换来的是系统闪屏退场时海报已经在位；
 * Coil 异步解码则会出现「先空一帧底色、海报才浮上来」的跳动。
 *
 * 顺带记一笔：不要在 FilterQuality 上再调参 —— Android 上 Low/Medium/High 逐像素相同，
 * 结论见 docs/2026-10-30-画质增强方案.md。
 */
@Composable
private fun SplashPoster() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val aspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 0f
        // 系统闪屏是一整块同色底，直接换成有内容的画面会「啪」一下。给它 180ms 淡入：
        // 底色一致，所以看起来只是人物从背景里浮出来。
        // 用 graphicsLayer 而不是 Modifier.alpha —— 后者会让每一帧重组这个子树。
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val alpha = animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = LinearEasing),
            label = "splashAlpha",
        )
        Image(
            painter = painterResource(R.drawable.launch_poster),
            // 纯装饰图：读屏在这里没有可读内容，别让它停顿
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value },
            contentScale = if (aspect < 1f) ContentScale.Crop else ContentScale.Fit,
            alignment = Alignment.Center,
        )
    }
}
