package com.fakenews.comics.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.waterfall
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakenews.comics.data.model.ChapterDto
import com.fakenews.comics.data.session.ChapterView
import com.fakenews.comics.data.session.ReaderMode
import com.fakenews.comics.ui.common.AppSheet
import com.fakenews.comics.ui.common.ChapterLayoutGlyph
import com.fakenews.comics.ui.common.ChevronsGlyph
import com.fakenews.comics.ui.common.Settings2Glyph
import com.fakenews.comics.ui.common.ChevronGlyph
import com.fakenews.comics.ui.common.CloseGlyph
import com.fakenews.comics.ui.common.EaseOutQuint
import com.fakenews.comics.ui.common.HistoryGlyph
import com.fakenews.comics.ui.common.GlassTint
import com.fakenews.comics.ui.common.ToastCenter
import com.fakenews.comics.ui.common.ToastHost
import com.fakenews.comics.ui.common.glass
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.TabularNums
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHROME_TIMEOUT_MS = 3_000L

/** 续读提示卡的驻留时长：带按钮的卡要给够读 + 点的时间（Web useReaderPrompt 同值） */
private const val PROMPT_TTL_MS = 6_000L

/** 续读告知卡的内容（对齐 Web ReaderPrompt：只用于「你在这里」+ 一个回到开头的出口） */
private data class ReaderPrompt(
    val title: String,
    val desc: String,
    val actionLabel: String,
    val onAction: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ReaderViewModel = hiltViewModel(),
) {
    // **不强制深色主题。** 阅读器画布是黑的，很容易顺手写成 FakenewsTheme(darkTheme = true)，
    // 但那样章节抽屉/阅读设置也会在浅色主题下变成深面板 —— Web 端专门去掉过这层包裹
    // （见 ReaderShell.tsx:412-416 的注释：「弹层和 App 不是一个主题」是我改之前的真问题）。
    // 正确做法是把「恒黑画布上的控制层」显式刷成白色（见下面的 LocalContentColor + 各处 Color.White），
    // 面板继续跟随全局主题。
    /*
     * 退出前先把系统栏要回来。
     *
     * 阅读器是全屏沉浸：控件 3 秒后自动隐藏（CHROME_TIMEOUT_MS），系统栏一并 hide。
     * 而详情页靠 `windowInsetsPadding(statusBars)` 布局。如果等阅读器**完全离开组合**
     * （ImmersiveMode 的 onDispose）才恢复系统栏，详情页在 popEnter 时已经以 inset=0
     * 画完首帧，随后系统栏回来 → 整页下移一次。这就是「阅读一会儿返回详情页会抖」：
     * 停留超过 3 秒才会进入沉浸态，所以只有读久了才复现。
     *
     * 在 pop 之前就 show，详情页 popEnter 组合时拿到 insets 已经是正确值，
     * 不会再出现「先上移再回来」。
     */
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window
    val exitReader = remember(onBack, view, window) {
        {
            window?.let { w ->
                WindowCompat.getInsetsController(w, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            onBack()
        }
    }
    ReaderContent(exitReader, modifier, vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    onBack: () -> Unit,
    modifier: Modifier,
    vm: ReaderViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var chromeVisible by remember { mutableStateOf(true) }
    var chapterSheet by remember { mutableStateOf(false) }
    var settingsSheet by remember { mutableStateOf(false) }
    val sheetOpen = chapterSheet || settingsSheet
    val toast = remember { ToastCenter() }
    // 毛玻璃浮层（续读卡/轻提示/toast）都采样页面层：页面层挂 .haze(hazeState)，
    // 浮层挂 .glass(hazeState, ...)
    val hazeState = remember { HazeState() }

    LaunchedEffect(Unit) { vm.start() }
    // 每条提示一个协程：串行的话第二条要等第一条走完 2.4 秒才出现（Web 是多条并存的）
    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> launch { toast.show(msg.text, msg.kind) } }
    }
    /*
     * 轻提示：居中玻璃胶囊（Web `.reader-notice`）。`collectLatest` 保证新提示
     * 立刻顶掉上一条（连按两次翻页不该排队等前一条走完 1.5s）。
     */
    var notice by remember { mutableStateOf<ReaderNoticeView?>(null) }
    var noticeSeq by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        vm.notices.collectLatest { n ->
            noticeSeq += 1
            notice = ReaderNoticeView(noticeSeq, n.text, n.dir)
            delay(1500) // Web：showNotice 的默认 1500ms
            if (notice?.id == noticeSeq) notice = null
        }
    }

    ImmersiveMode(chromeVisible || sheetOpen)
    /*
     * 系统返回键也走统一出口（onBack 已经是「恢复系统栏 + pop」）。
     * 弹层打开时不拦截 —— 让章节抽屉 / 设置面板自己消费返回键先关面板。
     */
    BackHandler(enabled = !sheetOpen) { onBack() }
    KeepScreenOn(state.settings.keepScreenOn)
    LaunchedEffect(state.chapterIndex, state.imageIndex, state.pageCount, state.settings.preload) {
        vm.preloadPages(state.imageIndex, state.settings.preload)
    }

    // 退到后台就落定页码：这是进程被回收前最后一次上报机会
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.flushReport()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.flushReport()
        }
    }

    LaunchedEffect(chromeVisible, sheetOpen, state.pageIndex, state.chapterIndex) {
        if (chromeVisible && !sheetOpen) {
            delay(CHROME_TIMEOUT_MS)
            chromeVisible = false
        }
    }

    // 阅读器整屏进入：淡入 + 极轻缩放（1.015 → 1），240ms —— 对齐 Web .anim-reader-in
    val readerIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        readerIn.animateTo(1f, animationSpec = tween(240, easing = EaseOutQuint))
    }
    // 续读告知：落到恢复出来的页码时告诉用户「你在这里」+ 一个回到开头的出口
    var prompt by remember { mutableStateOf<ReaderPrompt?>(null) }
    var promptDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.pages.isEmpty(), state.pageIndex) {
        if (promptDone || state.loading || state.pages.isEmpty()) return@LaunchedEffect
        promptDone = true
        if (state.pageIndex > 0) {
            prompt = ReaderPrompt(
                // Web ReaderShell.tsx:159-163 的原文：已续读 / 上次读到第 X / Y 页 / 从头开始
                title = "已续读",
                desc = "上次读到第 ${state.pageIndex + 1} / ${state.modePageCount} 页",
                actionLabel = "从头开始",
                onAction = {
                    vm.seek(0)
                    prompt = null
                },
                onDismiss = { prompt = null },
            )
        }
    }
    LaunchedEffect(prompt) {
        if (prompt != null) {
            delay(PROMPT_TTL_MS)
            prompt = null
        }
    }

    // 阅读器根节点是裸 Box，没有 Surface 提供内容色：不补这一句，标题和返回箭头会画成透明。
    // 恒给白色（不是 onSurface）—— 这块区域永远是黑底，浅色主题下 onSurface 是近黑色，等于隐形。
    // 弹层（章节抽屉 / 设置）在下面另起，不在这个 Local 的作用域里，仍按主题走。
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .graphicsLayer {
                    val t = readerIn.value
                    alpha = t
                    scaleX = 1f + 0.015f * (1f - t)
                    scaleY = 1f + 0.015f * (1f - t)
                    transformOrigin = TransformOrigin.Center
                },
        ) {
            /*
             * 页面层是 Haze 的采样源：续读卡要真的做背景模糊（见 ReaderPromptCard），
             * 卡身是半透明玻璃，没有这层 source 就只能靠提高不透明度硬盖。
             * 只包页面本身，不包工具栏与卡片自身（source 包住自己会自我引用）。
             */
            Box(
                Modifier
                    .fillMaxSize()
                    /*
                     * **只在续读卡在屏上时挂采样源。** haze 的 source 一旦常驻，阅读器静止时
                     * 仍在重录图层：实测 2 秒 22 帧（约 11fps），去掉后 0 帧 —— 和
                     * `CircularProgressIndicator` 那次的空转是同一类问题，别让它常驻。
                     * 卡片入场有 300ms 淡入，source 第一帧还没采到内容也看不出来。
                     */
                    .then(
                        if (prompt != null || notice != null || toast.items.isNotEmpty()) {
                            Modifier.haze(hazeState)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    state.pages.isEmpty() -> ReaderFallback(
                        message = state.error ?: "页面加载失败",
                        onRetry = vm::retry,
                        onBack = onBack,
                    )

                    else -> {
                        val toggle = { chromeVisible = !chromeVisible }
                        // 点屏翻页后收起工具栏（与 Web 端一致）
                        val turn = { delta: Int ->
                            vm.turn(delta)
                            chromeVisible = false
                        }
                        /*
                         * 工具栏淡入淡出 200ms（Web `ReaderShell.tsx:310/353`：顶/底栏都是
                         * `transition-opacity duration-200`，隐藏时额外加 `pointer-events-none`）。
                         *
                         * 之前是 `if (chromeVisible)` 直接挂摘：点中间唤出工具栏是「啪」地整块出现，
                         * 上下两道黑色渐变遮罩同时硬切，和阅读器其它动效（翻页、提示卡）不在一个语言里。
                         * 淡出期间保持挂载，所以 200ms 内它仍然吃点击 —— 用户刚操作完工具栏，
                         * 这点保守是想要的；完全透明后才不渲染，点中间照样能重新唤出。
                         */
                        val chromeAlpha by animateFloatAsState(
                            targetValue = if (chromeVisible) 1f else 0f,
                            animationSpec = tween(200, easing = EaseOutQuint),
                            label = "readerChrome",
                        )
                        // 换章、换模式都整块重建：页码域变了，重定位比逐项同步可靠
                        key(state.chapterIndex, state.settings.mode) {
                            if (state.settings.mode == ReaderMode.WEBTOON) {
                                WebtoonPager(
                                    pageIndex = state.pageIndex,
                                    pageCount = state.pageCount,
                                    urlFor = vm::pageUrl,
                                    onSettle = vm::observePage,
                                    onTapCenter = toggle,
                                    pinchZoom = state.settings.pinchZoom,
                                )
                            } else {
                                ModeStage(
                                    mode = state.settings.mode,
                                    pageIndex = state.pageIndex,
                                    imageCount = state.pageCount,
                                    settings = state.settings,
                                    urlFor = vm::pageUrl,
                                    onPage = vm::observePage,
                                    onEdge = vm::crossChapter,
                                    onTurn = turn,
                                    onTapCenter = toggle,
                                    resetKey = state.chapterIndex,
                                    canEdgePrev = state.chapterIndex > 0,
                                    canEdgeNext = state.chapterIndex < state.chapters.lastIndex,
                                )
                            }
                        }
                        if (state.switching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        if (chromeAlpha > 0.01f) {
                            ReaderChrome(
                                title = state.comicTitle,
                                subtitle = state.chapter
                                    ?.let {
                                        "${state.chapterIndex + 1}/${state.chapters.size} ${it.title}" +
                                            " · 第 ${state.pageIndex + 1}/${state.modePageCount} 页"
                                    }
                                    ?: "第 ${state.pageIndex + 1}/${state.modePageCount} 页",
                                pageIndex = state.pageIndex,
                                total = state.modePageCount,
                                canPrevChapter = state.chapterIndex > 0,
                                canNextChapter = state.chapterIndex < state.chapters.lastIndex,
                                onBack = onBack,
                                onOpenChapters = { chapterSheet = true },
                                onOpenSettings = { settingsSheet = true },
                                onPrevChapter = { vm.openChapter(state.chapterIndex - 1) },
                                onNextChapter = { vm.openChapter(state.chapterIndex + 1) },
                                onSeek = vm::seek,
                                alpha = chromeAlpha,
                            )
                        }
                    }
                }
            }

            // 顶部进度线：**常驻**（Web 在工具栏之外单独一层），沉浸阅读时也看得到读到哪
            if (state.pages.isNotEmpty()) {
                TopProgressLine(
                    fraction = progressOf(state.pageIndex, state.modePageCount),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            ToastHost(
                center = toast,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                hazeState = hazeState,
            )

            // 续读提示卡：从底部浮入，停在工具栏上方，不遮画面正中
            prompt?.let { p ->
                ReaderPromptCard(
                    prompt = p,
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp),
                )
            }

            // 轻提示：居中玻璃胶囊。声明在最后 = 盖在工具栏之上（Web 是 z-30，header 是 z-10）
            notice?.let { ReaderNotice(it, hazeState) }
        }
    }

    if (chapterSheet) {
        // 标题栏右侧的「列表 / 四格」切换（Web 章节抽屉的 headerRight：
        // `btn-ghost px-2 py-1 text-xs` + 15px 图标 + 4dp 间距）
        AppSheet(
            title = "章节（${state.chapters.size}）",
            onDismiss = { chapterSheet = false },
            headerRight = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .pressClickable(onClickLabel = "切换章节布局") {
                            vm.setChapterView(
                                if (state.chapterView == ChapterView.GRID) ChapterView.LIST else ChapterView.GRID,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    ChapterLayoutGlyph(
                        grid = state.chapterView == ChapterView.LIST,
                        size = 15.dp,
                        color = LocalInk.current.ink300,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (state.chapterView == ChapterView.GRID) "列表" else "四格",
                        fontSize = 12.sp,
                        color = LocalInk.current.ink300,
                    )
                }
            },
        ) {
            ChapterSheet(
                chapters = state.chapters,
                current = state.chapterIndex,
                view = state.chapterView,
                onSelect = { index ->
                    chapterSheet = false
                    vm.openChapter(index)
                },
            )
        }
    }

    if (settingsSheet) {
        AppSheet(title = "阅读设置", onDismiss = { settingsSheet = false }) {
            ReaderSettingsSheet(settings = state.settings, onChange = vm::updateSettings)
        }
    }
}

/**
 * 续读告知卡（逐值对齐 Web `.reader-prompt`，`index.css:708-753` + `ReaderPrompt.tsx:99-144`）：
 * - 容器：`min(92vw, 24rem)` 宽、`padding .7/.8/.75rem`、14dp 圆角、
 *   `bg rgb(18 20 26 / .72)` + `backdrop-blur(18px)`（见下）、`border 1px white/12`、大投影；
 * - 结构：History 图标（**brand-300**）+ 13sp 半粗标题 + 11.5sp white/60 正文 + 右上关闭 X；
 * - 动作：右下角 32dp 高、9dp 圆角、`bg white/10` + `border white/14` 的**次级**按钮
 *   （Web 这个动作没传 primary，所以不是品牌色实心 —— 之前画成了橙色主按钮）；
 * - 入场 300ms：上浮 14dp + 0.97→1 放大。
 *
 * 走底部而不是居中：带按钮的卡居中会压在画面正中，把正在看的那一页整个盖住。
 *
 * **毛玻璃靠 haze 真模糊**（`hazeChild` 采样页面层；源是 `ReaderContent` 里那层
 * `Box` 上的 `.haze(hazeState)`，只在卡片在屏时才挂，原因见那里的注释），
 * tints 里那层 0.72 的深色等于 Web 的
 * `background: rgb(18 20 26 / .72)`，`blurRadius = 18dp` 等于 `backdrop-filter: blur(18px)`。
 *
 * 为什么不能省掉模糊、只把底色改成不透明：卡片钉在底部 92dp、卡高 ~64dp，漫画页底边
 * 经常正好横穿它 —— 半透明而没有模糊时，上半截透出白纸、下半截压在纯黑上，卡里就出现
 * 一条白色长方形（2026-10-30 用户报的）；可硬盖成不透明又把玻璃质感一起盖没了（用户当场否）。
 * 正解是**模糊**：页边那道硬分界被 18dp 半径糊成渐变，与 Web 完全同构。
 * `fallbackTint` 是 API < 32 不支持 RenderEffect 时的退路，取 0.96 近不透明（宁可没玻璃感，
 * 也不能出白条）。
 */
@Composable
private fun ReaderPromptCard(
    prompt: ReaderPrompt,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val brand = LocalBrandColors.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, animationSpec = tween(300, easing = EaseOutQuint))
    }
    val t = anim.value
    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = t
                translationY = 14.dp.toPx() * (1f - t)
                val s = 0.97f + 0.03f * t
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin.Center
            }
            .widthIn(max = 384.dp) // min(92vw, 24rem)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(14.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.92f),
            )
            // 统一玻璃外观（ui/common/Glass.kt）：模糊 + 0.72 深色 tint = Web 的
            // backdrop-blur(18px) + rgb(18 20 26 / .72)。边框跟在后面，被裁掉外侧半像素。
            .glass(hazeState, RoundedCornerShape(14.dp), GlassTint, alpha = 0.72f, blurRadius = 18.dp)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            HistoryGlyph(
                size = 15.dp,
                color = brand.brand300,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    prompt.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                )
                Text(
                    prompt.desc,
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // 关闭 X（13dp，white/45）：Web 允许用户自己收起这张卡
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .pressClickable(onClickLabel = "关闭提示", onClick = prompt.onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                CloseGlyph(size = 13.dp, color = Color.White.copy(alpha = 0.45f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(9.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(9.dp))
                    .pressClickable(pressedScale = 0.96f, onClick = prompt.onAction)
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    prompt.actionLabel,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 上下两条工具栏。中间整片留空（只放一个无手势的 Spacer），点击照旧落到下面的翻页区。
 *
 * 两条 bar 自带吞点击的手势：不吞的话，点在标题旁边的空白处会被下面的 TapZone 当成翻页。
 */
@OptIn(ExperimentalMaterial3Api::class) // Slider 的 thumb / track 槽位还是实验 API
@Composable
private fun ReaderChrome(
    title: String,
    subtitle: String,
    pageIndex: Int,
    total: Int,
    canPrevChapter: Boolean,
    canNextChapter: Boolean,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeek: (Int) -> Unit,
    /** 显隐动画的当前透明度（调用方用 animateFloatAsState 驱动，见上面的 chromeAlpha） */
    alpha: Float = 1f,
) {
    // Web 的上下工具栏是「黑 85% → 45% → 透明」的竖直渐变 + 极轻模糊：
    // 文字够清楚，画面边缘又能透出来。纯色 72% 会在画面上下压出两条硬边。
    val brand = LocalBrandColors.current
    val topScrim = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.85f),
        0.55f to Color.Black.copy(alpha = 0.45f),
        1f to Color.Transparent,
    )
    val bottomScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.45f to Color.Black.copy(alpha = 0.45f),
        1f to Color.Black.copy(alpha = 0.85f),
    )
    val swallowTaps = Modifier.pointerInput(Unit) { detectTapGestures { } }
    // 挖孔 / 曲面（水滴屏、瀑布屏）也在安全区内：横屏时返回键、翻章键不能落进孔里或曲面上
    val safeChrome = WindowInsets.safeDrawing.union(WindowInsets.waterfall)
    Column(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        Column(
            Modifier
                .fillMaxWidth()
                // 先铺渐变、再吃内边距：顺序反过来渐变会从状态栏下沿才开始，
                // 顶上留下一条纯黑的硬边（Web 的渐变是盖到屏幕顶边的）
                .background(topScrim)
                .windowInsetsPadding(safeChrome.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .then(swallowTaps),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    // Web 用的是 lucide ChevronLeft 20px（细折线），不是 M3 的实心三角箭头
                    ChevronGlyph(
                        left = true,
                        size = 20.dp,
                        color = Color.White,
                        modifier = Modifier.semantics { contentDescription = "退出阅读" },
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        // Web 是 text-sm font-medium（半粗会显得比 Web 更重）
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        // Web 是 `text-[11px] text-white/60`（默认 400），labelSmall 自带 500
                        fontWeight = FontWeight.Normal,
                        // 阅读器恒黑底，用白 60% 而不是主题灰
                        color = Color.White.copy(alpha = 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onOpenChapters) {
                    // Web 的章节目录按钮是 lucide 的 List（三行「点 + 线」）
                    ChapterLayoutGlyph(
                        grid = false,
                        size = 19.dp,
                        color = Color.White,
                        modifier = Modifier.semantics { contentDescription = "章节目录" },
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    // Web 是 lucide `Settings2`（两条横轨 + 两个圆）；M3 的实心齿轮
                    // 和顶栏另外两个细线图标不是同一套视觉语言
                    Settings2Glyph(
                        size = 19.dp,
                        color = Color.White,
                        modifier = Modifier.semantics { contentDescription = "阅读设置" },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bottomScrim)
                .windowInsetsPadding(safeChrome.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .then(swallowTaps)
                .padding(horizontal = 12.dp, vertical = 10.dp), // Web py-2.5 = 10px
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChapterNavButton(
                text = "上一章",
                leading = true,
                enabled = canPrevChapter,
                onClick = onPrevChapter,
            )
            if (total > 1) {
                Slider(
                    value = pageIndex.toFloat().coerceIn(0f, (total - 1).toFloat()),
                    onValueChange = { onSeek(it.roundToInt()) },
                    valueRange = 0f..(total - 1).toFloat(),
                    // 不传 steps：M3 会按 steps 画出刻度圆点，Web 的原生 range 只有一条光轨。
                    // thumb/track 也换成自定义的细轨 + 圆点 —— M3 默认是「粗轨 + 条形滑块」，
                    // 放进去像另一个产品的控件（Web 就是浏览器原生的 <input type=range>）。
                    // 拖拽手势在 Slider 外层，不在 thumb 上，所以自绘不影响拖动。
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = brand.brand500,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                    thumb = {
                        // Chromium 下 accent-color 会把滑块本身也染成品牌色（白圈是 iOS 的观感）
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(brand.brand500),
                        )
                    },
                    track = { sliderState -> ReaderSliderTrack(sliderState) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            // Web：`w-14 text-right text-[11px] tabular-nums text-white/70`
            Text(
                "${pageIndex + 1}/$total",
                // tabular-nums：Web 那行有，之前只抄了宽度没抄等宽，翻到两位数时整串会跳
                style = TabularNums,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.70f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
            ChapterNavButton(
                text = "下一章",
                leading = false,
                enabled = canNextChapter,
                onClick = onNextChapter,
            )
        }
        }
    }

/**
 * 底部「上一章 / 下一章」（对齐 Web 的 `btn-ghost touch-hit p-2 text-white`）。
 *
 * 图标是 18dp 的自绘 chevron + 文字 14sp，禁用时整块压到 32% 透明度 ——
 * 之前用的是 M3 `TextButton`（没有图标、12dp 内距、字号 14sp 但字重与 Web 不同）。
 */
@Composable
private fun ChapterNavButton(
    text: String,
    leading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .pressClickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.32f)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp), // Web gap-1.5
    ) {
        if (leading) ChevronGlyph(left = true, size = 18.dp, color = Color.White)
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
        if (!leading) ChevronGlyph(left = false, size = 18.dp, color = Color.White)
    }
}

private fun progressOf(pageIndex: Int, total: Int): Float =
    if (total <= 1) 1f else ((pageIndex + 1).toFloat() / total).coerceIn(0f, 1f)

/**
 * 常驻顶部进度线（对齐 Web `ReaderShell` 里那条 `h-0.5` 的细线）：
 * 2dp 高、白色 10% 轨道 + 品牌色填充，用 **scaleX** 而不是改宽度（不触发布局）；
 * 300ms 过渡，工具栏收起（沉浸阅读）时仍然显示 —— 不然沉浸状态下完全不知道读到哪。
 *
 * **钉在屏幕最顶端（y=0），不吃状态栏内边距。** Web 是 `absolute top-0`，位置永远不动；
 * 早先写成 `statusBarsPadding()`，工具栏一唤出、系统栏回来，这条线就整体下跳一个状态栏
 * 高度（看起来像「标题栏下面的一条橙色分割线」），与 Web 不符。
 */
/**
 * 阅读器底部滑杆的轨道：一条 4dp 的圆头细线（白 18% 打底 + 品牌色填充）。
 *
 * M3 默认轨道是「16dp 粗轨 + 两端留出滑块缺口」，与浏览器原生 range
 * （细轨 + 圆点）完全不是一个观感；Web 端用的正是原生 range，所以这里自绘。
 * 拖拽手势挂在 Slider 外层（不在 thumb 上），自绘 thumb/track 不影响拖动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSliderTrack(state: SliderState) {
    val brand = LocalBrandColors.current
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val span = state.valueRange.endInclusive - state.valueRange.start
        val frac = if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
        val cy = size.height / 2f
        val stroke = 4.dp.toPx()
        drawLine(
            color = Color.White.copy(alpha = 0.18f),
            start = Offset(0f, cy),
            end = Offset(size.width, cy),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (frac > 0f) {
            drawLine(
                color = brand.brand500,
                start = Offset(0f, cy),
                end = Offset(size.width * frac, cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TopProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val brand = LocalBrandColors.current
    val scale by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = EaseOutQuint),
        label = "readerTopBar",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = scale
                }
                .background(brand.brand500),
        )
    }
}

/**
 * 章节抽屉内容：列表（一行一章）/ 四格（一行四格，章多时一屏看更多）。
 * 与本机记忆的 chapterView 联动，与详情页那份分开存（Web 同一论证）。
 */
@Composable
private fun ChapterSheet(
    chapters: List<ChapterDto>,
    current: Int,
    view: ChapterView,
    onSelect: (Int) -> Unit,
) {
    if (chapters.isEmpty()) {
        // Web `ChapterDrawer`：px-4 py-6 text-center text-sm text-ink-500
        Text(
            "未找到章节",
            fontSize = 14.sp,
            color = LocalInk.current.ink500,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        )
        return
    }

    if (view == ChapterView.GRID) {
        // 四格：直接铺满抽屉宽度，行内 4 等分；章数上百时用 LazyColumn 按行懒加载。
        // Web `grid grid-cols-4 gap-2 p-3` —— 8dp 间距 + 12dp 内距（之前内距写成 20dp）。
        val rows = chapters.chunked(4)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows.size) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows[r].forEach { chapter ->
                        val active = chapter.index == current
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (active) {
                                        LocalBrandColors.current.brand600
                                    } else {
                                        LocalInk.current.ink800
                                    },
                                )
                                .pressClickable(pressedScale = 0.96f) { onSelect(chapter.index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${chapter.index + 1}",
                                // Web ChapterDrawer 的序号是 `tabular-nums`：两位数章节时
                                // 序号列不会把标题整体推走
                                style = TabularNums,
                                fontSize = 14.sp, // Web text-sm
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                // Web：在读白字，其余 text-ink-200
                                color = if (active) Color.White else LocalInk.current.ink200,
                            )
                        }
                    }
                    repeat(4 - rows[r].size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        ) {
            itemsIndexed(chapters) { index, chapter ->
                val active = index == current
                val brand = LocalBrandColors.current
                val ink = LocalInk.current
                // 对齐 Web ChapterDrawer 的列表项：整行高 2dp 左边框 + 在读章 10% 品牌底，
                // 标题与右侧 meta 分列两端；行间 `divide-y divide-ink-800` 一条分隔线。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(if (active) brand.brand600.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(if (active) brand.brand500 else Color.Transparent),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "${chapter.index + 1}. ${chapter.title}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) brand.brand400 else ink.ink200,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Web：`ml-3 flex shrink-0 items-center gap-2 text-[11px]`，
                    // 顺序是「当前」在前、「N 页」在后（Android 之前反了，且写成了「在读」）
                    Spacer(Modifier.width(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            Text("当前", fontSize = 11.sp, color = brand.brand400)
                        }
                        if (chapter.pageCount > 0) {
                            Text("${chapter.pageCount} 页", fontSize = 11.sp, color = ink.ink500)
                        }
                    }
                }
                if (index != chapters.lastIndex) {
                    HorizontalDivider(color = ink.ink800, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun ReaderFallback(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        /*
         * 阅读器是**固定黑底**，这里不能用随主题走的 ink 阶（浅色主题下 ink200 本身是深色）。
         * 也不能不写色：Icon 的默认 tint 与 Text 的默认色都取自 LocalContentColor，
         * 而阅读器这一层没有提供它 → 落到默认纯黑，等于在黑底上画黑字。
         */
        Icon(
            Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.White.copy(alpha = 0.80f),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.92f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text("重试") }
            TextButton(onClick = onBack) { Text("返回详情") }
        }
    }
}

/** 隐藏系统栏；唤出工具栏时再显示回来。 */
@Composable
private fun ImmersiveMode(showSystemBars: Boolean) {
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(showSystemBars, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val bars = WindowInsetsCompat.Type.systemBars()
        if (showSystemBars) controller?.show(bars) else controller?.hide(bars)
        onDispose { controller?.show(bars) }
    }
}

/**
 * 保持屏幕常亮。
 *
 * 阅读时用户不碰屏幕，系统超时灭屏是最容易被抱怨的一点；但不是所有人都想要——
 * 睡前在被窝里看的人恰恰希望它照常熄屏，所以做成开关（默认开），与 Web 端 `keepScreenOn` 同款。
 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window, enabled) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (enabled) window?.addFlags(flag) else window?.clearFlags(flag)
        onDispose { window?.clearFlags(flag) }
    }
}

/* ================= 轻提示（居中玻璃胶囊） ================= */

/** 渲染用的轻提示快照；[id] 只用来重播入场（同一条文字连着出现两次也要重新浮入） */
private data class ReaderNoticeView(val id: Int, val text: String, val dir: Int)

/**
 * 对齐 Web `.reader-notice`（`index.css:672`）+ `ReaderShell.tsx:393-411`：
 *
 *   居中、`rounded-full`、`px-3.5 py-2`、12.5sp、white/92；
 *   底色 `rgb(20 22 28 / .55)` + `border white/12` + 内高光 + 外投影（玻璃质感）；
 *   到头提示的描边掺品牌色（`--brand-300 / .22`），并从**拉动的那一侧**浮入 14px；
 *   入场 280ms：opacity 0→1、位移 ±14px/6px → 0、scale .94 → 1。
 *
 * 与底部 toast 的分工：这条是「阅读流里的瞬时反馈」，出现在画面正中、1.5s、无按钮。
 */
@Composable
private fun ReaderNotice(notice: ReaderNoticeView, hazeState: HazeState?) {
    val brand = LocalBrandColors.current
    val alpha = remember(notice.id) { Animatable(0f) }
    val t = remember(notice.id) { Animatable(0f) }
    LaunchedEffect(notice.id) {
        alpha.animateTo(1f, tween(280, easing = EaseOutQuint))
    }
    LaunchedEffect(notice.id) {
        t.animateTo(1f, tween(280, easing = EaseOutQuint))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .widthIn(max = 320.dp) // Web max-width: 82vw（手机约 320dp）
                .graphicsLayer {
                    this.alpha = alpha.value
                    val k = 1f - t.value
                    translationX = notice.dir * 14.dp.toPx() * k
                    translationY = 6.dp.toPx() * k
                    val sc = 0.94f + 0.06f * t.value
                    scaleX = sc
                    scaleY = sc
                }
                // Web `.reader-notice`：rgb(20 22 28 / .55) + backdrop-blur(16px)
                .glass(hazeState, CircleShape, GlassTint, alpha = 0.55f, blurRadius = 16.dp)
                .border(
                    width = 1.dp,
                    color = if (notice.dir != 0) {
                        brand.brand300.copy(alpha = 0.22f)
                    } else {
                        Color.White.copy(alpha = 0.12f)
                    },
                    shape = CircleShape,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp), // Web gap-2
        ) {
            // Web：方向图标品牌色（暗示「就是这道边」），只有到头提示才带
            if (notice.dir < 0) ChevronsGlyph(left = true, size = 14.dp, color = brand.brand300)
            Text(
                notice.text,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.12.sp, // Web letter-spacing 0.01em
            )
            if (notice.dir > 0) ChevronsGlyph(left = false, size = 14.dp, color = brand.brand300)
        }
    }
}
