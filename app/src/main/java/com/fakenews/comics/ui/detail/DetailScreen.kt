package com.fakenews.comics.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakenews.comics.data.model.ChapterDto
import com.fakenews.comics.data.model.ComicDetailDto
import com.fakenews.comics.data.session.ChapterView
import com.fakenews.comics.ui.comic.ComicCover
import com.fakenews.comics.ui.common.ChevronGlyph
import com.fakenews.comics.ui.common.BrandButton
import com.fakenews.comics.ui.common.ChapterLayoutGlyph
import com.fakenews.comics.ui.common.ContentFadeIn
import com.fakenews.comics.ui.common.DetailSkeleton
import com.fakenews.comics.ui.common.EmptyView
import com.fakenews.comics.ui.common.ErrorView
import com.fakenews.comics.ui.common.GhostButton
import com.fakenews.comics.ui.common.GroupedCardRow
import com.fakenews.comics.ui.common.LocalToast
import com.fakenews.comics.ui.common.MascotKind
import com.fakenews.comics.ui.common.PageAmbient
import com.fakenews.comics.ui.common.RotateCcwGlyph
import com.fakenews.comics.ui.common.TagGlyph
import com.fakenews.comics.ui.common.iconBump
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.common.spin
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.TabularNums
import com.fakenews.comics.ui.theme.accentTextColor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 收藏胭脂红（Web --fav-500，固定语义色，不随高亮色变） */
private val FavRed = Color(0xFFF03F24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    comicId: String,
    onBack: () -> Unit,
    onOpenReader: (chapterId: String, page: Int) -> Unit,
    modifier: Modifier = Modifier,
    vm: DetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    LaunchedEffect(comicId) { vm.load(comicId) }
    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> launch { toast?.show(msg.text, msg.kind) } }
    }
    // 只有服务端真的存上了才收面板：保存失败面板留在原地，用户不必重填一遍
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.saved.collect { editing = false }
    }

    Box(modifier.fillMaxSize()) {
    /*
     * 环境光必须从**屏幕最顶端**起画 —— 状态栏、顶栏都在它之上。
     *
     * 之前它挂在下面的 Column 里（顶栏以下的那块），而四层色团的中心全在盒子顶边
     * 之上（fy 为负），顶边那一行还有三成亮度：光晕的上沿正好压在顶栏底边，
     * 成了横贯整屏的一条硬边（实测该行像素从 (22,25,35) 直接跳到 (40,45,58)，
     * 深色主题下尤其明显）。顶栏本来就是透明的，本来就是为了让光透上来。
     */
    if (state.detail != null) {
        PageAmbient(Modifier.align(Alignment.TopCenter))
    }
    Column(
        // 顶栏的避让从 Scaffold 挪到这里：DetailRoute 不再整页让出状态栏，
        // 否则光晕到不了顶部（详见 DetailRoute 的注释）
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
    TopAppBar(
        // M3 默认 titleLarge 22sp，是全站唯一一处这么大的字。Web 详情页没有标题栏，
        // 这里统一到「页面级标题」口径：16sp / 600（与登录标题、确认框标题同档）。
        title = {
            Text(
                "详情",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                // 自绘图不给 contentDescription，无障碍标签挂在按钮本身上
                modifier = Modifier.semantics { contentDescription = "返回" },
            ) {
                // lucide `ChevronLeft` 的细折线（与阅读器返回键同款）。
                // 核心集的 `KeyboardArrowLeft` 是实心三角，并排看是两种视觉语言。
                ChevronGlyph(left = true, size = 20.dp, color = LocalInk.current.ink200)
            }
        },
        // 顶栏透明：让详情页顶部的环境光（PageAmbient）透上来，与 Web 同款
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        // Scaffold 已经处理过状态栏内边距，顶栏再吃一次会多出一条空白
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
    when {
        state.loading -> DetailSkeleton()
        // 详情拿不到分两种情况：请求失败（网络/服务端）与确实不存在。混在一起会让
        // NAS 断网看起来像「这本书被删了」，所以按 error 是否为空分开画。
        state.detail == null && state.error != null -> ErrorView(
            title = "详情加载失败",
            message = state.error,
            hint = "稍后重试；如果反复失败，确认 NAS 上的服务与漫画目录挂载正常",
            onRetry = vm::retry,
        )
        state.detail == null -> EmptyView(
            title = "漫画不存在",
            hint = "可能已被移动或删除",
            mascot = MascotKind.NOT_FOUND,
        )
        else -> {
            val detail = state.detail!!
            // 骨架 → 真实内容是**同一次路由内**的整棵子树替换，路由淡入覆盖不到这里。
            // 没有这层淡入时详情页加载完会硬切一下（Web `pages/Detail.tsx:152-155`
            // 的 anim-content-in 就是为这个加的，同样不带位移）
            ContentFadeIn(Modifier.fillMaxWidth()) {
                DetailContent(
                    detail = detail,
                    coverUrl = vm.coverUrl(),
                    favoritePending = state.favoritePending,
                    resetting = state.resetting,
                    resetError = state.resetError,
                    saving = state.saving,
                    rescanning = state.rescanning,
                    editing = editing,
                    chapterView = state.chapterView,
                    onChapterViewChange = vm::setChapterView,
                    onToggleFavorite = vm::toggleFavorite,
                    onResetProgress = vm::resetProgress,
                    onRescan = vm::rescan,
                    onEdit = { editing = true },
                    onDismissEdit = { editing = false },
                    onOpenReader = { chapter, page -> onOpenReader(chapter.id, page) },
                    onSaveMeta = vm::saveMeta,
                )
            }
        }
    }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    detail: ComicDetailDto,
    coverUrl: String?,
    favoritePending: Boolean,
    resetting: Boolean,
    resetError: String?,
    saving: Boolean,
    rescanning: Boolean,
    editing: Boolean,
    chapterView: ChapterView,
    onChapterViewChange: (ChapterView) -> Unit,
    onToggleFavorite: () -> Unit,
    onResetProgress: () -> Unit,
    onRescan: () -> Unit,
    onEdit: () -> Unit,
    onDismissEdit: () -> Unit,
    onOpenReader: (ChapterDto, Int) -> Unit,
    onSaveMeta: (title: String, author: String, description: String, tags: List<String>) -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    // 本次弹窗内是否已经点过「重置」：成功后据此自动关闭，失败时据此显示原因。
    // 不能只看 `resetting` 的 true→false —— 首次打开弹窗时它本来就是 false。
    var resetAttempted by remember { mutableStateOf(false) }
    val progress = detail.progress
    val resumeChapter = detail.chapters.firstOrNull { it.id == progress?.chapterId }
    ?: detail.chapters.firstOrNull()
    val resumePage = if (resumeChapter != null && progress?.chapterId == resumeChapter.id) progress.pageIndex else 0

    Box(Modifier.fillMaxSize()) {
    // 环境光不在这里：它挂在 DetailScreen 的根 Box 上，要从屏幕顶端（状态栏后面）起画，
    // 挂在这一层就又被顶栏挡住了上沿。
    LazyColumn(
        // 宽屏（平板 / 折叠屏展开）把内容收在 960dp 内居中，否则两栏会被拉得过宽
        modifier = Modifier
            .widthIn(max = 960.dp)
            .fillMaxSize()
            .align(Alignment.TopCenter),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        /*
         * 不整体 spacedBy：Web 的区块间距并不统一 —— 封面↔文字 16、文字↔章节 20、
         * 章节标题↔列表 8、四格的格间距 8、**列表视图的章节行之间是 0**（它们是一张
         * 连续的卡，行间只有一条 divide-y 细线）。用统一的 16dp 会把章节行拉开成
         * 「一叠独立的小卡」，和 Web 完全不是一个东西。改为各 item 自己带间距。
         */
        verticalArrangement = Arrangement.Top,
    ) {
    /*
     * 封面：手机断点是**上下堆叠**，封面 `aspect-[3/4] w-40`（160dp）水平居中
     * （Web `flex flex-col gap-4 sm:flex-row` 的默认形态）。
     *
     * 之前 Android 写成「封面 116dp 在左 + 标题在右」的两栏 —— 那是 Web **桌面**断点
     * （`sm:` 以上、封面 w-48）的形态，而且封面尺寸也小了一号。
     */
    /*
     * 封面 + 信息区：手机断点上下堆叠（Web `flex-col`，封面 160dp 居中），
     * 宽度 ≥600dp 走两栏（Web `sm:flex-row` 的桌面形态，封面 220dp 靠左）。
     *
     * 两栏的判定放在 item 内部用 BoxWithConstraints 量，而不是外层再包一层：
     * LazyColumn 的 contentPadding 已经扣掉了左右各 16dp，所以这里的 maxWidth
     * 比屏幕窄 32dp，阈值要相应让掉。
     */
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= WideBreakpoint - 32.dp
            val topPad = if (wide) 0.dp else 16.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    DetailCover(coverUrl, detail.title, 220.dp)
                    Box(Modifier.weight(1f)) {
                        DetailInfo(
                        detail = detail,
                        resumeChapter = resumeChapter,
                        resumePage = resumePage,
                        favoritePending = favoritePending,
                        rescanning = rescanning,
                        onToggleFavorite = onToggleFavorite,
                        onOpenReader = onOpenReader,
                        onReset = { confirmReset = true },
                        onEdit = onEdit,
                        onRescan = onRescan,
                        topPadding = topPad,
                        bottomPadding = 20.dp,
                    )
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    // 间距只由 DetailInfo 的 topPadding 给：这里再加一层 bottom 会让手机断点上
                    // 封面与标题之间变成 32dp（原设计是 16dp）
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DetailCover(coverUrl, detail.title, 160.dp)
                    }
                    DetailInfo(
                        detail = detail,
                        resumeChapter = resumeChapter,
                        resumePage = resumePage,
                        favoritePending = favoritePending,
                        rescanning = rescanning,
                        onToggleFavorite = onToggleFavorite,
                        onOpenReader = onOpenReader,
                        onReset = { confirmReset = true },
                        onEdit = onEdit,
                        onRescan = onRescan,
                        topPadding = topPad,
                        bottomPadding = 20.dp,
                    )
                }
            }
        }
    }

    item {
        // 章节标题行自带 `mb-2`（8dp）—— Web 的 section header 与列表之间只有 8dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            // Web 的标题前面有一个 lucide ListOrdered（15px）
            ChapterLayoutGlyph(
                grid = false,
                size = 15.dp,
                color = LocalInk.current.ink300,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "章节（${detail.chapters.size}）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalInk.current.ink300,
            )
            Spacer(Modifier.weight(1f))
            if (detail.chapters.isNotEmpty()) {
                // 列表 / 四格切换（Web 详情页右上角的按钮）：章多时四格一屏能看更多。
                // 图标画的是**目标视图**（当前在四格就显示列表图标），与文字一致。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .pressClickable(onClickLabel = "切换章节布局") {
                            onChapterViewChange(
                                if (chapterView == ChapterView.GRID) ChapterView.LIST else ChapterView.GRID,
                            )
                        }
                        // Web：btn-ghost 的 px-2 py-1
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    // Web: btn-ghost px-2 py-1 text-xs text-ink-400，图标 15
                    ChapterLayoutGlyph(
                        grid = chapterView == ChapterView.LIST,
                        size = 15.dp,
                        color = LocalInk.current.ink400,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (chapterView == ChapterView.GRID) "列表" else "四格",
                        fontSize = 12.sp,
                        color = LocalInk.current.ink400,
                    )
                }
            }
        }
    }

    if (detail.chapters.isEmpty()) {
        item {
            Text(
                "未找到章节，请重新扫描",
                fontSize = 14.sp,
                color = LocalInk.current.ink500,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        }
    } else if (chapterView == ChapterView.GRID) {
        // 四格：按行切块，每行 4 格。仍然是 LazyColumn 的 item，行级懒加载不丢。
        val rows = detail.chapters.chunked(4)
        itemsIndexed(rows, key = { i, _ -> "chapterRow$i" }) { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                // Web：`grid grid-cols-4 gap-2`（格间距 8dp，末行不留白）
                modifier = Modifier.padding(bottom = if (rowIndex == rows.lastIndex) 0.dp else 8.dp),
            ) {
                row.forEach { chapter ->
                    ChapterCell(
                        chapter = chapter,
                        current = progress?.chapterId == chapter.id,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val page = if (progress?.chapterId == chapter.id) progress.pageIndex else 0
                            onOpenReader(chapter, page)
                        },
                    )
                }
                // 末行不足 4 格：补空位，避免最后几格被拉宽
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else {
        itemsIndexed(detail.chapters, key = { _, it -> it.id }) { index, chapter ->
            // 行与行之间没有间距：它们是同一张 `.card` 的相邻段，拼起来才连续
            GroupedCardRow(
                isFirst = index == 0,
                isLast = index == detail.chapters.lastIndex,
            ) {
                ChapterRow(
                    chapter = chapter,
                    current = progress?.chapterId == chapter.id,
                    // Web 只在「在读且没读完」时显示「在读」角标
                    currentUnfinished = progress?.chapterId == chapter.id &&
                        (progress?.percent ?: 0.0) < 100.0,
                    onClick = {
                        val page = if (progress?.chapterId == chapter.id) progress.pageIndex else 0
                        onOpenReader(chapter, page)
                    },
                )
            }
        }
    }
    }
    }

    if (editing) {
    EditMetaSheet(
        draft = EditDraft(
            title = detail.title,
            author = detail.author.orEmpty(),
            description = detail.description.orEmpty(),
            tags = detail.tags,
        ),
        saving = saving,
        onDismiss = onDismissEdit,
        onSave = { draft ->
            onSaveMeta(draft.title, draft.author, draft.description, draft.tags)
        },
    )
    }

    // 成功（resetError 为空）才自动关闭；失败保持打开，原因写在框里 ——
    // 与 Web ConfirmDialog 一致（`finally` 里关掉的话，用户只看到「点了没反应」）
    LaunchedEffect(confirmReset, resetting, resetError, detail.progress) {
    if (!confirmReset) {
        resetAttempted = false
    } else if (resetAttempted && !resetting && resetError == null) {
        confirmReset = false
        resetAttempted = false
    }
    }

    if (confirmReset) {
    AlertDialog(
        onDismissRequest = { if (!resetting) confirmReset = false },
        // Web ConfirmDialog：标题 text-base font-semibold（16/600）、正文 text-sm（14）。
        // M3 的 AlertDialog 标题默认 headlineSmall 24sp，比 Web 大两号。
        title = { Text("重置阅读进度", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "将清除这部漫画的阅读记录（章节、页码、进度），收藏不受影响。确定继续？",
                    fontSize = 14.sp,
                )
                if (resetAttempted && !resetting && resetError != null) {
                    // Web ConfirmDialog 的 error 块：`rounded-lg border-red-500/40
                    // bg-red-500/10 px-2.5 py-2 text-[11px] leading-relaxed text-red-300`
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.10f))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.40f), shape)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(resetError, fontSize = 11.sp, lineHeight = 17.sp, color = Color(0xFFFCA5A5))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    resetAttempted = true
                    onResetProgress()
                },
                enabled = !resetting,
                // Web ConfirmDialog 的 `danger`：破坏性操作的确认键是红色
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(if (resetting) "重置中…" else "重置")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { confirmReset = false },
                enabled = !resetting,
                // Web `.btn-ghost`：中性文字色，不是品牌橙（M3 TextButton 默认取 primary）
                colors = ButtonDefaults.textButtonColors(contentColor = LocalInk.current.ink300),
            ) { Text("取消") }
        },
    )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsRow(
    detail: ComicDetailDto,
    resumeChapter: ChapterDto?,
    resumePage: Int,
    favoritePending: Boolean,
    rescanning: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenReader: (ChapterDto, Int) -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = detail.progress
    val percent = progress?.percent ?: 0.0
    val resuming = progress != null && percent > 0 && percent < 100
    val ink = LocalInk.current
    /*
     * 收藏成功的「弹一下」信号（Web `pages/Detail.tsx:236-262`：成功后 setFavBump(true)
     * → 爱心挂 anim-icon-bump，onAnimationEnd 再摘掉）。
     *
     * 判定条件必须是「pending 由 true 落到 false **且** 收藏状态真的翻转了」：
     * 失败时 isFavorite 不变，不该弹。用自增计数而不是布尔 —— 布尔只能表达
     * 「刚弹过」，连点两次收藏第二次就没有信号了。
     */
    var bumpSeq by remember { mutableIntStateOf(0) }
    var prevFav by remember { mutableStateOf(detail.isFavorite) }
    var prevPending by remember { mutableStateOf(favoritePending) }
    LaunchedEffect(detail.isFavorite, favoritePending) {
    if (prevPending && !favoritePending && detail.isFavorite != prevFav) bumpSeq++
    prevFav = detail.isFavorite
    prevPending = favoritePending
    }
    FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
    // 主行动：章未就绪时禁用 —— 点了静默无反应比禁用更糟
    BrandButton(
        text = if (resuming) "继续阅读" else "开始阅读",
        onClick = { resumeChapter?.let { onOpenReader(it, resumePage) } },
        enabled = resumeChapter != null,
        icon = Icons.Filled.PlayArrow,
        // Web：百分比是独立的 `text-xs opacity-80` 小字，不是并进正文字号
        trailing = if (resuming) {
            {
                Text(
                    "（${percent.roundToInt()}%）",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.80f),
                )
            }
        } else {
            null
        },
    )
    // 收藏用胭脂红（固定语义色），不跟「高亮颜色」变：换主题色时爱心仍该是爱心
    GhostButton(
        text = if (detail.isFavorite) "已收藏" else "收藏",
        onClick = onToggleFavorite,
        enabled = !favoritePending,
        iconContent = {
            if (favoritePending) {
                // 请求中把爱心换成转圈：只靠 disabled（整颗淡到 45%）在弱网下
                // 看起来像「点了没反应」。尺寸与爱心一致（15dp），按钮宽度不跳
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 1.5.dp,
                    color = ink.ink400,
                )
            } else {
                Icon(
                    if (detail.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (detail.isFavorite) FavRed else ink.ink300,
                    // 切换成功后弹一下；首次进入详情页不弹（iconBump 内部记账）
                    modifier = Modifier.size(15.dp).iconBump(bumpSeq),
                )
            }
        },
    )
    // Web 按钮顺序：开始阅读 → 收藏 → 编辑信息 → 重扫 → 重置进度
    GhostButton(
        text = "编辑信息",
        onClick = onEdit,
        icon = Icons.Filled.Create,
    )
    GhostButton(
        text = if (rescanning) "重扫中…" else "重扫",
        onClick = onRescan,
        enabled = !rescanning,
        iconContent = {
            // 核心图标集里没有 Autorenew/Loop（都在 extended），顺时针的 Refresh 代理「重扫」。
            // 重扫期间让它转起来（Web：`animate-spin`，1s 线性一圈）——
            // 只换文案不给动效的话，看起来像按钮被禁用了而不是在工作
            if (rescanning) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = ink.ink300,
                    modifier = Modifier.size(15.dp).spin(),
                )
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = ink.ink300,
                    modifier = Modifier.size(15.dp),
                )
            }
        },
    )
    if (progress != null) {
        // 逆时针的 RotateCcw 是自绘字形（ImageVector 走不通），单独包一层同样的按钮外壳
        ResetProgressButton(onReset)
    }
    }
}

/** 「重置进度」用的逆时针箭头（Web `RotateCcw`）—— GhostButton 只收 ImageVector，
 *  这里是 Canvas 自绘的，所以单独包一层同样的按钮外壳。 */
@Composable
private fun ResetProgressButton(onReset: () -> Unit) {
    val ink = LocalInk.current
    Row(
    modifier = Modifier
        .height(44.dp)
        .pressClickable(onClick = onReset)
        .border(1.dp, ink.ink800, RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    ) {
    RotateCcwGlyph(size = 15.dp, color = ink.ink300)
    Spacer(Modifier.width(6.dp))
    Text("重置进度", color = ink.ink300, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 标签行（对齐 Web `Detail.tsx:202-211`）：
 * `mt-2 flex-wrap gap-1.5` + 前置 Tag 图标 12px ink-500，
 * 每个标签 `rounded bg-ink-800 px-1.5 py-0.5 text-[10px] text-ink-400`。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsRow(tags: List<String>) {
    val ink = LocalInk.current
    FlowRow(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.padding(top = 8.dp),
    ) {
    TagGlyph(
        size = 12.dp,
        color = ink.ink500,
        modifier = Modifier.align(Alignment.CenterVertically),
    )
    tags.forEach { tag ->
        Text(
            tag,
            fontSize = 10.sp, // Web text-[10px]
            color = ink.ink400,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(ink.ink800)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
    }
}

/**
 * 章节行（列表视图，对齐 Web `pages/Detail.tsx:340`）：
 * 整列包在圆角卡片里（首行圆上角、末行圆下角，行与行之间一条分割线），
 * 在读章左侧一条整行高的 2dp 品牌色竖线（Web 的 `border-l-2 border-brand-500`）。
 *
 * 三处照抄 Web 的细节，之前都做错了：
 *   1. 在读章标题是 **brand-400** 而不是 brand-500（同一条 border 用 500，文字用 400）；
 *   2. 右侧 meta 顺序是「N 页」在前、「在读」在后（`gap-2` 8dp，都是 12sp ink-500）；
 *   3. 「在读」只在**没读完**时出现（`progress.percent < 100`），读完了就只剩页数。
 */
@Composable
private fun ChapterRow(
    chapter: ChapterDto,
    current: Boolean,
    currentUnfinished: Boolean,
    onClick: () -> Unit,
) {
    val brand = LocalBrandColors.current
    val ink = LocalInk.current
    Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        // Web 这里挂的是 `.press`（按压缩放），不是裸 clickable —— 四格那边同理
        .pressClickable(onClickLabel = "阅读第 ${chapter.index + 1} 章", onClick = onClick)
        .padding(end = 16.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    ) {
    Box(
        Modifier
            .width(2.dp)
            .fillMaxHeight()
            .background(if (current) brand.brand500 else Color.Transparent),
    )
    // Web：`border-l-2 px-4` —— 2dp 竖线之外还有 16dp 内距，文字距卡边 18dp
    Spacer(Modifier.width(16.dp))
    Text(
        "${chapter.index + 1}. ${chapter.title}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (current) accentTextColor() else ink.ink200,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(12.dp))
    if (chapter.pageCount > 0) {
        Text(
            "${chapter.pageCount} 页",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp,
            color = ink.ink500,
        )
    }
    if (currentUnfinished) {
        Spacer(Modifier.width(8.dp))
        Text(
            "在读",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp,
            color = accentTextColor(),
        )
    }
    }
}

/** 章节格（四格视图，对齐 Web）：固定 44dp 高、8dp 圆角，在读章实心品牌色 + 白字。 */
@Composable
private fun ChapterCell(
    chapter: ChapterDto,
    current: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val brand = LocalBrandColors.current
    Box(
    modifier = modifier
        .height(44.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(
            // Web：选中 `bg-brand-600`、未选中 `bg-ink-800`（不是 surfaceVariant 的近似值）
            if (current) brand.brand600 else LocalInk.current.ink800,
        )
        .pressClickable(pressedScale = 0.96f, onClickLabel = "阅读第 ${chapter.index + 1} 章", onClick = onClick),
    contentAlignment = Alignment.Center,
    ) {
    Text(
        "${chapter.index + 1}",
        // Web Detail 的章节序号是 `tabular-nums`
        style = TabularNums,
        fontSize = 14.sp, // Web text-sm
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        // Web：在读白字，其余 text-ink-200（不是 onSurface 的 ink-100）
        color = if (current) Color.White else LocalInk.current.ink200,
        maxLines = 1,
    )
    }
}

@Composable
private fun MetaText(text: String) {
    // Web 元信息行：text-xs text-ink-400
    Text(
    text,
    fontSize = 12.sp,
    color = LocalInk.current.ink400,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    )
}

private fun formatSize(bytes: Long): String =
    if (bytes <= 0) "—" else "%.1f MB".format(bytes / 1024.0 / 1024.0)


/** 两栏断点：≥600dp（平板竖屏、横屏手机）把封面与信息并排，章节列表仍在下方占整行。 */
private val WideBreakpoint = 600.dp

/**
 * 详情页大封面。宽度由调用方给：手机断点 160dp（Web `w-40`）、两栏 220dp。
 * Web 详情页大封面是 rounded-2xl（16dp），网格卡片才是 rounded-xl —— 别跟着网格的圆角走。
 */
@Composable
private fun DetailCover(coverUrl: String?, title: String, width: Dp) {
    ComicCover(
    coverUrl,
    title,
    Modifier.width(width),
    shape = RoundedCornerShape(16.dp),
    )
}

/** 标题 / 原名 / 元信息 / 标签 / 简介 / 操作按钮。手机与两栏共用，靠 topPadding 对齐。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailInfo(
    detail: ComicDetailDto,
    resumeChapter: ChapterDto?,
    resumePage: Int,
    favoritePending: Boolean,
    rescanning: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenReader: (ChapterDto, Int) -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit,
    onRescan: () -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    // Web 里标题/标签/简介/按钮在同一个 `min-w-0 flex-1` 容器内，块间距是各自的
    // mt-0.5 / mt-2 / mt-3 / mt-4，所以这里用一组 top 值而不是统一的 spacedBy。
    // 上下间距由调用方给：手机断点 top=16dp，两栏时 top=0 与封面顶对齐。
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding),
    ) {
        Text(
            detail.title,
            // Web: text-xl font-bold（20px / 行高 28），颜色继承 body 的 text-ink-100
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            /*
             * **必须显式给色。** 宿主 Scaffold 是 `containerColor = Transparent`，
             * `contentColorFor(Transparent)` 返回 Unspecified，Surface 因此不覆写
             * LocalContentColor，裸 Text 就落到 CompositionLocal 的默认值 **纯黑**：
             * 浅色下比 Web 的 #111827 更黑一档，深色下是黑字压黑底
             * （2026-10-30 用户报的「标题不随主题变化、和 Web 不一样」）。
             * ink100 正是 Web 的 `--ink-100`：浅 #111827 / 深 #ECEEF2，
             * 还跟着主题切换的 200ms 插值走。
             */
            color = LocalInk.current.ink100,
        )
        if (detail.rawTitle.isNotBlank() && detail.rawTitle != detail.title) {
            Text(
                "原名：${detail.rawTitle}",
                // Web: mt-0.5 text-xs text-ink-500
                fontSize = 12.sp,
                color = LocalInk.current.ink500,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // Web: mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-ink-400
        // 用「·」拼成一行的话，窄屏上 MB 会被截掉；Web 是 flex-wrap，
        // 折行而不是省略号 —— FlowRow 同一语义。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            detail.author?.takeIf { it.isNotBlank() }?.let { MetaText("作者：$it") }
            detail.format.uppercase().takeIf { it.isNotBlank() }?.let { MetaText(it) }
            MetaText("${detail.chapterCount} 章")
            MetaText("${detail.pageCount} 页")
            // 目录型漫画没有单个文件大小，别显示一个空破折号
            if (detail.sizeBytes > 0) MetaText(formatSize(detail.sizeBytes))
        }
        if (detail.tags.isNotEmpty()) {
            TagsRow(detail.tags) // 自带 padding(top = 8.dp)，对应 Web 的 mt-2
        }
        // Web 的顺序是：标题/元信息/标签 → 简介 → 按钮组（简介在按钮之上）
        detail.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                desc,
                // Web: mt-3 text-sm leading-relaxed text-ink-300
                fontSize = 14.sp,
                lineHeight = 23.sp,
                color = LocalInk.current.ink300,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        ActionsRow(
            detail = detail,
            resumeChapter = resumeChapter,
            resumePage = resumePage,
            favoritePending = favoritePending,
            rescanning = rescanning,
            onToggleFavorite = onToggleFavorite,
            onOpenReader = onOpenReader,
            onReset = onReset,
            onEdit = onEdit,
            onRescan = onRescan,
            modifier = Modifier.padding(top = 16.dp), // Web mt-4
        )
    }
    
}

