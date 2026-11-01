package com.fakenews.comics.ui.shelf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fakenews.comics.data.model.HistoryItemDto
import com.fakenews.comics.ui.comic.ComicCard
import com.fakenews.comics.ui.common.BackToTopButton
import com.fakenews.comics.ui.common.rememberBackToTopVisible
import com.fakenews.comics.ui.common.ComicGridSkeleton
import com.fakenews.comics.ui.common.EaseOutQuint
import com.fakenews.comics.ui.common.EmptyView
import com.fakenews.comics.ui.common.ErrorView
import com.fakenews.comics.ui.common.GroupedCardRow
import com.fakenews.comics.ui.common.LocalToast
import com.fakenews.comics.ui.common.RefreshBox
import com.fakenews.comics.ui.common.RiseIn
import com.fakenews.comics.ui.common.SkeletonBox
import com.fakenews.comics.ui.common.ToastKind
import com.fakenews.comics.ui.common.formatDay
import com.fakenews.comics.ui.LocalBottomBarInset
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import kotlinx.coroutines.launch

/** 收藏心形用的品牌红（Web `text-fav-500`），与详情页「已收藏」同值。 */
private val FavRed = Color(0xFFF03F24)

/** 网格列数（Web `.comic-grid` 手机端 3 列） */
private const val GRID_COLUMNS = 3

/**
 * 书架页：**一个页面装两个区块** —— 我的收藏（网格）+ 阅读记录（整表卡片）。
 *
 * 这是 Web `pages/Shelf.tsx` 的真实结构：`<div className="flex flex-col gap-7">` 里
 * 两个 `<section>`，区块间距 28dp。
 *
 * 之前 Android 把这两个 section 提升成了底栏的两个独立 tab（「收藏」「记录」），
 * 于是底栏变成 书架/收藏/记录/我的 —— 和 Web 的 首页/书架/搜索/漫画库/设置 只重合一项。
 * 现在回到 Web 的划分：收藏与记录是「书架」的内容，不是导航项。
 *
 * 实现上有个取舍：记录区的 `.card`（整表一张卡 + 行间 `divide-y`）在 Compose 的
 * LazyColumn 里没法用一个容器跨 item 包住（容器会失去 lazy）。所以改成「首行上圆角、
 * 中间行直角、末行下圆角 + 左右竖边逐行画」，观感与一张连续的卡一致，行间那条线
 * 用 `ink-800` 画在非末行的底边。这样记录区仍然是懒加载的，几百条也不会卡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onOpenComic: (String) -> Unit,
    onOpenReader: (comicId: String, chapterId: String, page: Int) -> Unit,
    /** 两个区块空态里的「去挑一本」：切到首页 tab（Web 是指向 `/` 的链接） */
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    favVm: FavoritesViewModel = hiltViewModel(),
    histVm: HistoryViewModel = hiltViewModel(),
) {
    val fav by favVm.state.collectAsStateWithLifecycle()
    val hist by histVm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        favVm.messages.collect { msg -> launch { toast?.show(msg.text, msg.kind) } }
    }
    LaunchedEffect(Unit) {
        histVm.messages.collect { msg ->
            when (msg) {
                is HistoryMessage.Undoable -> scope.launch {
                    // 多条撤销提示并存（Web 的 toast.undo 同样如此）：ToastCenter 最多
                    // 留 3 条，被挤掉的那条会拿到 false，走「落定删除」分支 ——
                    // 这正是想要的：用户看不到的提示不该还留着撤销窗口
                    val undone = toast?.show(
                        // Web：`已删除《${h.title}》的阅读记录`
                        text = "已删除《${msg.title}》的阅读记录",
                        kind = ToastKind.INFO,
                        actionLabel = "撤销",
                        durationMillis = 6000,
                    ) == true
                    if (undone) histVm.undo(msg.comicId) else histVm.commitDelete(msg.comicId)
                }
                is HistoryMessage.Toast -> launch { toast?.show(msg.text, msg.kind) }
            }
        }
    }
    // 离开书架（含切 tab）时把「还没落定」的删除提交掉，撤销窗口不能跨页面存活
    DisposableEffect(Unit) {
        onDispose {
            histVm.commitPendingDelete()
        }
    }
    // 从阅读器切回：刚读过的漫画要按新的阅读时间重新排位
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) histVm.refreshIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val favRows = remember(fav.items) { fav.items.chunked(GRID_COLUMNS) }
    /**
     * 收藏区是不是只剩「骨架 / 空态 / 错误」这一种形态（占一个 item）。
     * 只看 items 是否为空：下拉刷新时 items 非空，仍走网格分支，没有占位 item。
     */
    val favHasPlaceholder = fav.items.isEmpty()

    val listState = rememberLazyListState()
    // 滚过 480dp（Web 同款阈值）出现回顶按钮；首页/搜索/漫画库是同一个组件
    val showBackToTop = rememberBackToTopVisible(listState)
    // 记录区的 item 下标：收藏标题 + 收藏行 + 可能的占位 + 记录标题 之后才是第一行记录
    val firstHistoryIndex = 1 + favRows.size + (if (favHasPlaceholder) 1 else 0) + 1
    val lastVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    LaunchedEffect(lastVisible, hist.items.size, hist.hasMore, firstHistoryIndex) {
        if (hist.hasMore && lastVisible >= firstHistoryIndex + hist.items.size - 4) histVm.loadMore()
    }

    RefreshBox(
        // 两边的刷新是独立的：任一个在下拉刷新就转圈
        isRefreshing = fav.refreshing || hist.refreshing,
        onRefresh = {
            favVm.refresh(pullToRefresh = true)
            histVm.refresh(pullToRefresh = true)
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarInset.current),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ---- 我的收藏 ----
            item(key = "fav-header") {
                SectionHeader(
                    title = "我的收藏",
                    // Web 是 `({favs?.items.length ?? 0})`：加载失败时也显示 `(0)`，不隐藏
                    count = fav.items.size,
                    leading = {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = FavRed,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            when {
                fav.loading -> item(key = "fav-skeleton") { FavoritesSkeleton() }
                /*
                 * 不再套固定高度（320/330dp）的 Box：那是为了让 fillMaxSize 的空态
                 * 在 LazyColumn 里「有个高度」，代价是内容天然比盒子矮/高时都不居中，
                 * 且两块占位的高度是手调出来的。Web 的 ErrorState / EmptyState 本来就是
                 * **内容高度**（py-14 / py-20），交给 fillHeight=false 自己撑开即可。
                 */
                fav.items.isEmpty() && fav.error != null -> item(key = "fav-error") {
                    ErrorView(
                        title = "收藏加载失败",
                        message = fav.error,
                        onRetry = { favVm.refresh() },
                        fillHeight = false,
                    )
                }
                fav.items.isEmpty() -> item(key = "fav-empty") {
                    EmptyView(
                        title = "还没有收藏",
                        hint = "在详情页点「收藏」，漫画会出现在这里",
                        actionLabel = "去挑一本",
                        onAction = onBrowse,
                        actionArrow = true, // Web：`去挑一本 <ChevronRight size={14}/>`
                        fillHeight = false,
                    )
                }
                else -> itemsIndexed(favRows, key = { _, row -> row.first().id }) { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp), // Web gap-y-4
                        horizontalArrangement = Arrangement.spacedBy(12.dp), // Web gap-x-3
                    ) {
                        row.forEachIndexed { i, comic ->
                            // weight 必须挂在**外层 Box** 上，不能直接传给 ComicCard：
                            // ComicCard 内部先过一层 CardEntrance（Box），weight 是 RowScope
                            // 的扩展，穿出 Row 之后失效 —— 卡片会按内容宽度撑满整行
                            Box(Modifier.weight(1f)) {
                                ComicCard(
                                    comic = comic,
                                    coverUrl = favVm.coverUrl(comic.id),
                                    onClick = { onOpenComic(comic.id) },
                                    index = rowIndex * GRID_COLUMNS + i,
                                )
                            }
                        }
                        // 末行不满 3 个时补空位，否则两张卡会被拉成半屏宽
                        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // ---- 阅读记录 ----
            item(key = "hist-header") {
                SectionHeader(
                    title = "阅读记录",
                    // Web：`({historyTotal}{history.length > 0 && history.length < total ? ' · 已显示 M' : ''})`
                    // —— **总数为 0 时也显示 `(0)`**，只有加载失败才不显示
                    countText = if (hist.error == null) {
                        val shown = hist.items.size
                        val suffix = if (shown > 0 && shown < hist.total) " · 已显示 $shown" else ""
                        "(${hist.total}$suffix)"
                    } else {
                        null
                    },
                    // 两个区块之间 `gap-7` = 28dp：收藏最后一行已有 16dp 底距，这里再补 12dp
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            when {
                hist.loading -> item(key = "hist-skeleton") { HistorySkeleton() }
                hist.items.isEmpty() && hist.error != null -> item(key = "hist-error") {
                    ErrorView(
                        title = "阅读记录加载失败",
                        message = hist.error,
                        onRetry = { histVm.refresh() },
                        fillHeight = false,
                    )
                }
                hist.items.isEmpty() -> item(key = "hist-empty") {
                    EmptyView(
                        title = "没有阅读记录",
                        hint = "开始阅读后，进度会自动出现在这里",
                        actionLabel = "去挑一本",
                        onAction = onBrowse,
                        actionArrow = true, // 同上
                        fillHeight = false,
                    )
                }
                else -> {
                    itemsIndexed(hist.items, key = { _, it -> "h-${it.id}" }) { index, item ->
                        // 逐行错峰（Web `animate-rise` + `(i % 12) * 14ms`）：
                        // 用页内序号取模，续拉第二页时同样是「依次浮现」而不是整页同时冒出
                        RiseIn(delayMillis = (index % 12) * 14) {
                            GroupedCardRow(
                                isFirst = index == 0,
                                isLast = index == hist.items.lastIndex,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                HistoryRow(
                                    item = item,
                                    coverUrl = histVm.coverUrl(item.id),
                                    // 记录的主用途是「接着上次读」：整行点下去直接续读，
                                    // 想看简介/章节就点封面进详情（次级入口，不占版面）
                                    onRead = { onOpenReader(item.id, item.chapterId, item.pageIndex) },
                                    onOpenDetail = { onOpenComic(item.id) },
                                    onDelete = { histVm.delete(item) },
                                )
                            }
                        }
                    }
                    // Web：`isFetchingNextPage` 或「到底了且满过一页」才有状态行；
                    // 无条件插入时，短列表底下会多出 32dp 的空白
                    if (hist.loadingMore ||
                        hist.loadMoreFailed ||
                        (!hist.hasMore && hist.items.size >= PAGE_SIZE)
                    ) {
                        item(key = "hist-footer") {
                            ListFooter(hist, histVm)
                        }
                    }
                }
            }
        }
        // 回顶按钮只盖滚动区，不进滚动容器：否则它会跟着内容一起滑走。
        // RefreshBox 的 content 是 BoxScope，直接 align 就行。
        BackToTopButton(
            visible = showBackToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/**
 * 区块标题（Web 的 `<h1 className="mb-3 flex items-center gap-2 text-lg font-bold">`）：
 * 18sp 加粗 + `mb-3` 12dp + `gap-2` 8dp，计数是 14sp `ink-500` 且**在标题行内**。
 */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    countText: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val ink = LocalInk.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink.ink100)
        val label = countText ?: count?.let { "($it)" }
        if (label != null) {
            Text(label, fontSize = 14.sp, color = ink.ink500, maxLines = 1)
        }
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItemDto,
    coverUrl: String?,
    onRead: () -> Unit,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit,
) {
    val ink = LocalInk.current
    val percent = item.progress?.percent ?: 0.0
    val pages = item.progressPageCount.coerceAtLeast(1)
    // Web：`flex items-center gap-3 px-3 py-2.5` —— 行内 12dp 间距、12/10dp 内距
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressClickable(onClick = onRead)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面 `h-14 w-11`（56×44dp）+ `rounded`（4dp）。是行内唯一的详情入口。
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ink.ink800)
                .pressClickable(
                    pressedScale = 0.94f,
                    onClickLabel = "查看《${item.title}》详情",
                    onClick = onOpenDetail,
                ),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 14.sp, // Web text-sm font-medium
                fontWeight = FontWeight.Medium,
                color = ink.ink100,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Web 只有这一行副文案：「第 N 章 · 第 P/T 页 · 日期」。
            Text(
                "第 ${item.chapterIndex + 1} 章 · 第 ${item.pageIndex + 1}/$pages 页 · " +
                    formatDay(item.progress?.updatedAt ?: item.lastReadAt ?: 0L),
                fontSize = 11.sp,
                color = ink.ink500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp)) // mt-1
            RowProgressBar(percent.toFloat().coerceIn(0f, 100f))
        }
        // Web 删除按钮：`btn-ghost p-2 text-ink-500 hover:text-red-400` + Trash2 15px
        Box(
            modifier = Modifier
                // Web 是 `p-2` + `.touch-hit`（命中区补到 44）；视觉仍是 15dp 的图标
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .pressClickable(onClickLabel = "删除《${item.title}》的阅读记录") { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = ink.ink500,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * 记录行的细进度条：96×4dp、圆角、轨道 `ink-800`、填充实心品牌色。
 * 宽度用 500ms 过渡（Web `transition-[width] duration-500`），删记录撤销回来时能看出变化。
 */
@Composable
private fun RowProgressBar(percent: Float) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val target = (percent / 100f).coerceIn(0f, 1f)
    val w by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 500, easing = EaseOutQuint),
        label = "historyBar",
    )
    Box(
        Modifier
            .width(96.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(ink.ink800),
    ) {
        Box(
            Modifier
                .fillMaxWidth(w)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(brand.brand500),
        )
    }
}

@Composable
private fun ListFooter(state: HistoryUiState, vm: HistoryViewModel) {
    val ink = LocalInk.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp), // Web py-4
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Web 这一档就只有一行文字（`<p role=status>`），不配转圈：
            // 续拉发生在用户快滑到底时，一个 14dp 的转圈反而像页面卡住了
            state.loadingMore -> Text(
                "正在加载更早的记录…",
                fontSize = 11.sp,
                color = ink.ink500,
            )
            // Web 用文字按钮而不是描边按钮：这里只是「续拉失败」，不该比列表本身更重
            state.loadMoreFailed -> Text(
                "加载更多失败 · 点击重试",
                fontSize = 12.sp,
                color = ink.ink400,
                modifier = Modifier.pressClickable { vm.retryLoadMore() }.padding(8.dp),
            )
            /*
             * Web：`!hasNextPage && history.length >= 30` 才说这句话。
             * 记录本来就少（只读过两本）时「已经到底了」既没必要也没信息量，
             * 而且会和上面的空态/短列表挤在一起。
             */
            state.items.size >= PAGE_SIZE ->
                Text("已经到底了 · 共 ${state.total} 条", fontSize = 11.sp, color = ink.ink600)
        }
    }
}

/**
 * 收藏骨架 = Web 的 `ComicGridSkeleton count={6}`，直接用共享组件：
 * 两者现在是同一套尺寸（3:4 封面 + 12/10dp 文字条），且共享组件已经改成
 * Column + Row（不是嵌套 Lazy），放进 LazyColumn 里不会出现双向滚动。
 */
@Composable
private fun FavoritesSkeleton() {
    ComicGridSkeleton(count = 6, sidePadding = 16.dp)
}

/**
 * 记录骨架。Web 在这里也用了网格骨架，但记录区的成品是横条行 —— 占位若画成
 * 四张竖版封面，数据到达时整块会「变形」。保留横条形状，只把死灰色块换成
 * 共享的 [SkeletonBox]（带扫光），与网格骨架的质感保持一致。
 */
@Composable
private fun HistorySkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        repeat(4) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    Modifier.size(width = 44.dp, height = 56.dp),
                    RoundedCornerShape(4.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(
                        Modifier.fillMaxWidth(0.55f).height(12.dp),
                        RoundedCornerShape(3.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(
                        Modifier.fillMaxWidth(0.8f).height(9.dp),
                        RoundedCornerShape(3.dp),
                    )
                }
            }
        }
    }
}

/** 撤销窗口 6 秒（Web `TTL_ACTION`）；点「撤销」立即生效。逻辑见调用处的 Undoable 分支。 */
