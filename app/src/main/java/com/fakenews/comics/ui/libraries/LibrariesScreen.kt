package com.fakenews.comics.ui.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.fakenews.comics.data.model.LibraryDto
import com.fakenews.comics.data.model.ScanStatusDto
import com.fakenews.comics.ui.common.BackToTopButton
import com.fakenews.comics.ui.common.rememberBackToTopVisible
import com.fakenews.comics.ui.common.BrandButton
import com.fakenews.comics.ui.common.CardSurface
import com.fakenews.comics.ui.common.EmptyView
import com.fakenews.comics.ui.common.ErrorView
import com.fakenews.comics.ui.common.FolderPlusGlyph
import com.fakenews.comics.ui.common.GhostButton
import com.fakenews.comics.ui.common.LoadingView
import com.fakenews.comics.ui.common.LocalToast
import com.fakenews.comics.ui.common.MascotKind
import com.fakenews.comics.ui.common.RefreshBox
import com.fakenews.comics.ui.common.TrashGlyph
import com.fakenews.comics.ui.common.UploadGlyph
import com.fakenews.comics.ui.LocalBottomBarInset
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 扫描进行中的轮询间隔（与首页横幅同值）：足够让进度动起来，又不至于把 NAS 打满。 */
private const val SCAN_ACTIVE_MS = 800L

/** 空闲时的轮询间隔（Web 同值）：只为发现「别人从网页端点过扫描」。 */
private const val SCAN_IDLE_MS = 15_000L

/**
 * 漫画库页（Web `pages/Libraries.tsx`）—— 2026-10-28 起**全量对齐**：
 * 顶部「扫描 / 新建」，每张卡片「编辑 / 上传 / 删除 / 立即扫描此库」。
 *
 * 唯一保留的差异是排版：Web 是两列网格（`sm:grid-cols-2`），手机上按一列排 ——
 * 卡片里有长路径、不可访问告警和四个按钮，两位数宽度钉下去就只能一路截断，
 * 一行一张反而是好的那一边。
 *
 * 库卡片上的**不可访问目录告警**仍是这一页最有价值的一段信息：书架空了的时候，
 * 用户唯一能查到原因的入口就在这里 —— 卷没挂载、或 fnOS 没给这个目录授权。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(
    modifier: Modifier = Modifier,
    vm: LibrariesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> launch { toast?.show(msg.text, msg.kind) } }
    }
    // 扫描进度轮询：进行中 800ms、空闲 15s。
    // 顺带负责「扫描结束」的回执：这里看到 running 从 true 掉到 false 时补刷一次库列表，
    // 否则卡片上的「收录 N 部」会一直停在扫描前的数字（VM 里没法判断这个下降沿）。
    var sawRunning by remember { mutableStateOf(false) }
    // 只在 RESUMED 期间轮询：与首页 startScanWatch/stopScanWatch 同一套口径。
    // 之前只靠 LaunchedEffect 随页面销毁停止 —— 但 App 退到后台时 composition 还活着，
    // 于是后台照样每 15s 打一次 NAS。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state.scan?.running, lifecycleOwner) {
        // 必须写成循环。LaunchedEffect 只在 key 变化时重启 —— 之前这里只做了一次
        // `delay + refreshScan`，于是 running 不变（扫描从头到尾都是 true）时协程直接结束，
        // 进度条永远停在第一次读到的那一格。key 是 running，所以节奏切换仍由外层控制。
        val running = state.scan?.running == true
        if (sawRunning && !running) vm.refresh()
        sawRunning = running
        val interval = if (running) SCAN_ACTIVE_MS else SCAN_IDLE_MS
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(interval)
                vm.refreshScan()
            }
        }
    }

    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val scope = rememberCoroutineScope()

    // 弹层状态留在界面层：它们都是「一次性 UI」，进程重建后回到列表是合理的
    var editing by remember { mutableStateOf<LibraryDraft?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var delError by remember { mutableStateOf<String?>(null) }

    // 滚过 480dp（Web 同款阈值）出现回顶按钮；首页/书架/搜索是同一个组件
    val listState = rememberLazyListState()
    val showBackToTop = rememberBackToTopVisible(listState)

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Web 页面 `gap-5`：标题行与库卡片之间 20dp
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("漫画库", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink.ink100)
            Spacer(Modifier.weight(1f))
            ScanButton(
                busy = state.scanBusy,
                scan = state.scan,
                onClick = { vm.triggerScan() },
            )
            // Web 标题行的第二个按钮：`<FolderPlus size={15} /> 新建`
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pressClickable(
                        pressedScale = 0.97f,
                        durationMillis = 150,
                        onClickLabel = "新建漫画库",
                        onClick = { editing = LibraryDraft() },
                    )
                    .background(brand.brand600, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FolderPlusGlyph(size = 15.dp, color = Color.White)
                Text("新建", fontSize = 14.sp, color = Color.White)
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                // Web：`if (isLoading) return <Loading label="加载漫画库…" />`
                // —— 吉祥物（延迟 600ms 淡入）+ spinner + 文案，不是裸转圈
                state.loading -> LoadingView(label = "加载漫画库…")
                state.items.isEmpty() && state.error != null -> ErrorView(
                    title = "漫画库加载失败",
                    message = state.error,
                    hint = "服务可能正在忙，稍后再试",
                    onRetry = { vm.refresh() },
                )
                state.items.isEmpty() -> EmptyView(
                    title = "还没有漫画库",
                    hint = "新建一个库并指向 NAS 上的漫画目录（支持多目录）。" +
                        "若该目录在 fnOS「设置 → 应用 → 肥牛漫画 → 文件夹访问权限」中尚未授权，扫描会读不到内容。",
                    mascot = MascotKind.EMPTY,
                    actionLabel = "新建漫画库",
                    onAction = { editing = LibraryDraft() },
                )
                else -> RefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { vm.refresh(pullToRefresh = true) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 24.dp + LocalBottomBarInset.current,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.id }) { lib ->
                            LibraryCard(
                                lib = lib,
                                scanBusy = state.scanBusy,
                                onScan = { vm.triggerScan(lib.id) },
                                onEdit = {
                                    editing = LibraryDraft(
                                        id = lib.id,
                                        name = lib.name,
                                        type = lib.type,
                                        paths = lib.paths.joinToString("\n"),
                                    )
                                },
                                onUpload = { vm.openUpload(lib) },
                                onDelete = { delError = null; deleting = lib.id },
                            )
                        }
                    }
                }
            }
            // 回顶按钮只盖滚动区，不进滚动容器：否则它会跟着内容一起滑走。
            // RefreshBox / Box 的 content 都是 BoxScope，直接 align 就行。
            BackToTopButton(
                visible = showBackToTop,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }

    // ---- 弹层 ----

    editing?.let { draft ->
        LibraryFormSheet(
            vm = vm,
            initial = draft,
            saving = state.saving,
            onDismiss = { editing = null },
            onSave = { value ->
                scope.launch {
                    // 失败时保持面板打开（输入的内容还在），成功才收
                    if (vm.saveLibrary(value) == null) editing = null
                }
            },
        )
    }

    state.upload?.let { upload ->
        UploadSheet(
            upload = upload,
            onDismiss = vm::closeUpload,
            onAddFiles = vm::addUploadFiles,
            onNoteRejected = vm::noteRejected,
            onRemoveFile = vm::removeUploadFile,
            onStartUpload = vm::startUpload,
            onCancelUpload = vm::cancelUpload,
        )
    }

    // 「删除不可撤销」这件事要有二次确认；失败时弹窗保持打开并把原因写在里面，
    // 否则用户只看到「点了没反应」（Web ConfirmDialog 同一口径）
    if (deleting != null) {
        val target = state.items.firstOrNull { it.id == deleting }
        AlertDialog(
            onDismissRequest = { if (state.removingId == null) deleting = null },
            title = { Text("删除漫画库？", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "库内漫画条目及其阅读进度、收藏都会被清除（磁盘上的漫画文件不受影响）。",
                        fontSize = 14.sp,
                    )
                    if (target != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "「${target.name}」· 收录 ${target.comicCount} 部",
                            fontSize = 12.sp,
                            color = LocalInk.current.ink500,
                        )
                    }
                    if (delError != null) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(delError.orEmpty(), fontSize = 11.sp, color = Color(0xFFFCA5A5))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = deleting ?: return@TextButton
                        scope.launch {
                            delError = vm.deleteLibrary(id)
                            if (delError == null) deleting = null
                        }
                    },
                    enabled = state.removingId == null,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(if (state.removingId != null) "删除中…" else "删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = null },
                    enabled = state.removingId == null,
                    colors = ButtonDefaults.textButtonColors(contentColor = ink.ink300),
                ) { Text("取消") }
            },
        )
    }
}

/**
 * 顶部「扫描」按钮（Web `btn-ghost ring-1 ring-ink-800` + RefreshCw 15px）。
 * 扫描进行中时按钮右侧显示 `done/total`（Web 同款），文字在转圈时换成「扫描中…」。
 */
@Composable
private fun ScanButton(busy: Boolean, scan: ScanStatusDto?, onClick: () -> Unit) {
    val ink = LocalInk.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            // Web 是 `.btn-ghost ring-1 ring-ink-800`：**无填充**，只有一圈描边。
            // 之前多铺了一层 ink-900 底，深色主题下看着比旁边的标题重了一档。
            .border(1.dp, ink.ink800, shape)
            .pressClickable(
                enabled = !busy,
                pressedScale = 0.97f,
                durationMillis = 150,
                onClickLabel = "扫描漫画库",
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (busy) {
            // Web 就是把 RefreshCw 换成 animate-spin，颜色仍是按钮文字的 ink-300
            CircularProgressIndicator(
                Modifier.size(15.dp),
                strokeWidth = 1.6.dp,
                color = ink.ink300,
            )
        } else {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = ink.ink300,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            if (busy) "扫描中…" else "扫描",
            fontSize = 14.sp, // Web：.btn-ghost 默认 text-sm
            color = ink.ink300,
            maxLines = 1,
        )
        if (scan?.running == true && scan.total > 0) {
            Text("${scan.done}/${scan.total}", fontSize = 11.sp, color = ink.ink500, maxLines = 1)
        }
    }
}

/**
 * 库卡片（对齐 Web `card flex flex-col gap-2 p-4`）：
 * 名称 `text-sm font-semibold` + 类型徽章（`bg-ink-800` / 10sp `ink-400`）、
 * 路径 `text-[11px] ink-500` 每行一个（📁 前缀）、收录数、
 * 右上角「编辑 / 上传 / 删除」，底部「立即扫描此库」。
 */
@Composable
private fun LibraryCard(
    lib: LibraryDto,
    scanBusy: Boolean,
    onScan: () -> Unit,
    onEdit: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
) {
    val ink = LocalInk.current
    CardSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        lib.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink.ink100,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (lib.type == "chapter") "章节漫画" else "单篇漫画",
                        fontSize = 10.sp,
                        color = ink.ink400,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ink.ink800)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                // Web：编辑（文字按钮 text-xs）/ 上传 / 删除（两个 15px 图标按钮）
                Row {
                    IconAction(
                        label = "编辑漫画库",
                        onClick = onEdit,
                    ) {
                        Text("编辑", fontSize = 12.sp, color = ink.ink300)
                    }
                    IconAction(
                        label = if (lib.type == "chapter") "上传整部漫画" else "上传漫画",
                        onClick = onUpload,
                    ) {
                        UploadGlyph(size = 15.dp, color = ink.ink400)
                    }
                    IconAction(
                        label = "删除漫画库",
                        onClick = onDelete,
                    ) {
                        TrashGlyph(size = 15.dp, color = ink.ink400)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            // 路径逐行显示（Web 每行一个 div + truncate）
            lib.paths.forEach { p ->
                Text(
                    "📁 $p",
                    fontSize = 11.sp,
                    color = ink.ink500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (lib.inaccessiblePaths.isNotEmpty()) {
                // Web：告警块在全卡 `gap-2` 之外还有 `mt-1`，合计 12dp
                Spacer(Modifier.height(12.dp))
                InaccessibleWarning(lib.inaccessiblePaths)
            }

            Spacer(Modifier.height(8.dp))
            Text("收录 ${lib.comicCount} 部漫画", fontSize = 11.sp, color = ink.ink500)

            // 按钮 `mt-1` + 卡片 gap-2 = 12dp
            Spacer(Modifier.height(12.dp))
            GhostButton(
                text = "立即扫描此库",
                onClick = onScan,
                enabled = !scanBusy,
                small = true, // Web：btn-ghost text-xs
            )
        }
    }
}

/** Web 卡片右上角那排动作：44dp 的实心命中区里放 15dp 的图标/小字。 */
@Composable
private fun IconAction(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .pressClickable(pressedScale = 0.94f, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 不可访问目录告警（Web：`border-amber-500/40 bg-amber-500/10 text-amber-300`）。
 *
 * 文案照抄 Web 的那段排查指引 —— 这段字是「书架为什么是空的」的答案，
 * 不能简化成「目录不可访问」五个字。
 */
@Composable
private fun InaccessibleWarning(paths: List<String>) {
    // Web：`border-amber-500/40 bg-amber-500/10`，文字分两档 ——
    // 标题 `text-amber-300`(#fcd34d)，路径与正文 `text-amber-200/90`(#fde68a/90)。
    // 路径那行还是 `font-mono`：目录名里的 / 和 _ 在比例字体下容易看错。
    val amber500 = androidx.compose.ui.graphics.Color(0xFFF59E0B)
    val amber300 = androidx.compose.ui.graphics.Color(0xFFFCD34D)
    val amber200 = androidx.compose.ui.graphics.Color(0xFFFDE68A)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(amber500.copy(alpha = 0.10f))
            .border(1.dp, amber500.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            "⚠️ 以下目录无法访问，书架会保持为空：",
            fontSize = 11.sp,
            color = amber300,
        )
        Spacer(Modifier.height(4.dp))
        paths.forEach { p ->
            Text(
                p,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = amber200.copy(alpha = 0.90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "请到 fnOS「设置 → 应用 → 肥牛漫画 → 文件夹访问权限」中把该目录授权给本应用" +
                "（只读即可）；若为文件管理里的共享文件夹，还需在其属性勾选「允许 Docker 容器访问」。",
            fontSize = 11.sp,
            color = amber200.copy(alpha = 0.90f),
        )
    }
}
