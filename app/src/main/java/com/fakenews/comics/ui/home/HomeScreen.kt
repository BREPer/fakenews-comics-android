package com.fakenews.comics.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.fakenews.comics.data.model.HistoryItemDto
import com.fakenews.comics.data.model.ScanStatusDto
import com.fakenews.comics.ui.comic.ComicCard
import com.fakenews.comics.ui.comic.ComicCover
import com.fakenews.comics.ui.comic.ProgressBarStatic
import com.fakenews.comics.ui.common.BackToTopButton
import com.fakenews.comics.ui.common.rememberBackToTopVisible
import com.fakenews.comics.ui.common.BrandChip
import com.fakenews.comics.ui.common.ComicGridSkeleton
import com.fakenews.comics.ui.common.comicGridColumns
import com.fakenews.comics.ui.common.ContentFadeIn
import com.fakenews.comics.ui.common.ContinueRowSkeleton
import com.fakenews.comics.ui.common.EaseOutQuint
import com.fakenews.comics.ui.common.EmptyView
import com.fakenews.comics.ui.common.ErrorView
import com.fakenews.comics.ui.common.HistoryGlyph
import com.fakenews.comics.ui.common.LocalToast
import com.fakenews.comics.ui.common.MascotKind
import com.fakenews.comics.ui.common.RefreshBox
import com.fakenews.comics.ui.LocalBottomBarInset
import com.fakenews.comics.ui.LocalBottomBarTopInset
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.accentTextColor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenComic: (String) -> Unit,
    onOpenReader: (comicId: String, chapterId: String, page: Int) -> Unit,
    /** 搜索入口是**跳转**到独立的搜索页（Web 是指向 `/search` 的 Link），不是就地输入 */
    onOpenSearch: () -> Unit,
    /** 扫描横幅右侧的「查看」，Web 指向 `/libraries` */
    onOpenLibraries: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> launch { toast?.show(msg.text, msg.kind) } }
    }

    // 扫描观察器只在前台跑：App 退到后台、切到别的 tab（VM 因 saveState 还活着）
    // 都要停掉，否则空闲时每 15s 照样打一次 NAS。Web 的 useScanWatch 也是挂载才观察。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.startScanWatch()
            try {
                awaitCancellation()
            } finally {
                vm.stopScanWatch()
            }
        }
    }

    val gridState = rememberLazyGridState()
    /*
     * 切筛选要回到顶部 —— 但**只在筛选真的变了**时才回。
     *
     * 原来写的是 `LaunchedEffect(state.filter) { gridState.scrollToItem(0) }`：
     * LaunchedEffect 换 key 会重启，可它**首次组合时也必然跑一次**。而从详情页返回时
     * HomeScreen 的组合是新建的（NavHost 只保留返回栈条目和 saved state），于是
     * rememberLazyGridState 刚把位置恢复回去，这一行立刻又把它滚回顶部 ——
     * 表现就是「滚到一半点开一本、返回后在最顶上」。
     * 记一个「上次见到的筛选值」，首次组合时两者相等，不触发滚动。
     */
    var filterSeen by remember { mutableStateOf(state.filter) }
    LaunchedEffect(state.filter) {
        if (state.filter == filterSeen) return@LaunchedEffect
        filterSeen = state.filter
        gridState.scrollToItem(0)
    }
    val lastVisible by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    LaunchedEffect(lastVisible, state.items.size, state.hasMore) {
        if (state.hasMore && lastVisible >= state.items.size - 6) vm.loadMore()
    }
    // 滚过 480dp（Web 同款阈值）出现回顶按钮；书架/搜索/漫画库用的是同一个组件
    val showBackToTop = rememberBackToTopVisible(gridState)

    // 页面底色由 MainShell 的 AmbientBackdrop 统一铺（页面根节点只此一层，不重复叠）
    Box(modifier.fillMaxSize()) {
        /*
         * 整页滚动（对齐 Web `pages/Home.tsx`）：搜索入口、扫描横幅、继续阅读、筛选
         * chips、排序行、封面网格全在**同一个滚动容器**里，滚起来一起往上走。
         *
         * 之前是「头部固定 + 只有网格滚」：那些区块钉在屏幕上，和 Web 上「滑两屏后
         * 搜索框与 chips 早就滚出视口」不是同一件事，网格能用的高度也少了一截。
         * 头部整块做成 **一个** item 而不是几个，是为了让回顶按钮能直接拿
         * firstVisibleItemScrollOffset 当页面滚动量用（见上面的 showBackToTop）。
         */
        // 列数按布局期真实可用宽度算（分屏/折叠屏/改分辨率都对得上，见 ui/common/Layout.kt）
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = comicGridColumns(maxWidth)
            RefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::pullToRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                columns = cols,
                state = gridState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    // 底栏浮在内容上，最后一行要能滚到栏上方
                    bottom = 24.dp + LocalBottomBarInset.current,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeHeader(
                        state = state,
                        vm = vm,
                        onOpenSearch = onOpenSearch,
                        onOpenReader = onOpenReader,
                        onOpenLibraries = onOpenLibraries,
                    )
                }
                when {
                    state.loading -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ComicGridSkeleton(sidePadding = 0.dp)
                        }
                    }
                    state.items.isEmpty() && state.error != null -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ErrorView(
                                title = "漫画列表加载失败",
                                message = state.error,
                                // Web：正在扫大库时后端会忙到超时，这时「失败」其实是「等一下」
                                hint = "如果服务正在扫描大库，稍等一会儿再重试",
                                onRetry = vm::refresh,
                            )
                        }
                    }
                    // 空库时才需要知道「是不是正在扫」：scan 的订阅收在这个分支里，
                    // 有书的时候扫描进度更新不该动到下面的网格
                    state.items.isEmpty() -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyOrScanning(
                                filtered = hasFilters(state),
                                onClearFilters = vm::clearFilters,
                                onOpenLibraries = onOpenLibraries,
                                vm = vm,
                            )
                        }
                    }
                    else -> {
                        itemsIndexed(state.items, key = { _, comic -> comic.id }) { index, comic ->
                            ComicCard(
                                comic = comic,
                                coverUrl = vm.coverUrl(comic.id),
                                onClick = { onOpenComic(comic.id) },
                                index = index,
                            )
                        }
                        // Web `ComicListFooter`：`hasMore || loaded < firstPageSize` 时整块 null
                        if (state.loadingMore ||
                            state.loadMoreFailed ||
                            (!state.hasMore && state.items.size >= PAGE_SIZE)
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ListFooter(state, vm)
                            }
                        }
                    }
                }
            }
        }
    }

        // 回顶按钮只盖滚动区，不进滚动容器：否则它会跟着内容一起滑走。
        BackToTopButton(
            visible = showBackToTop,
            onClick = { scope.launch { gridState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/**
 * 首页头部（Web 里就是文档流最上面那几块，滚起来一起走）：
 * 搜索入口 → 扫描横幅 → 继续阅读 → 筛选 chips + 排序行。
 *
 * 与 Web 首页同序。扫描状态单独订阅（见 [ScanBannerSlot]）：扫描期间它每 800ms
 * 变一次，订阅必须收在这个小子树里，否则整页（含网格）跟着一起重组。
 */
@Composable
private fun HomeHeader(
    state: HomeUiState,
    vm: HomeViewModel,
    onOpenSearch: () -> Unit,
    onOpenReader: (comicId: String, chapterId: String, page: Int) -> Unit,
    onOpenLibraries: () -> Unit,
) {
    /*
     * 必须包一层 Column：一个 lazy item 里发多个**兄弟**节点时，它们的摆放方式不受
     * 我们控制（实测会把几块叠在一起、高度只取最大那块）。包成单子节点后，item 的高度
     * 就是这几块的累加，顺序也确定。
     */
    Column {
        // Web 首页**没有 h1**：进来第一个元素就是搜索入口，标题留给底栏的「首页」。
        // 之前这里顶着「书架」+ 总数，与底栏的「首页」自相矛盾。
        SearchEntryButton(onClick = onOpenSearch)

        ScanBannerSlot(
            vm = vm,
            onOpenLibraries = onOpenLibraries,
            modifier = Modifier.padding(top = 10.dp),
        )

        when {
            state.continueLoading -> ContinueRowSkeleton()
            // 骨架 → 真实横滑是**同一次路由内**的整棵子树替换，路由淡入覆盖不到这里：
            // 没有这层淡入时数据到达会硬切一下（Web 同一处也包了 anim-content-in）
            state.continueItems.isNotEmpty() -> ContentFadeIn(Modifier.fillMaxWidth()) {
                ContinueReadingRow(
                    items = state.continueItems,
                    coverUrl = vm::coverUrl,
                    onRead = onOpenReader,
                )
            }
        }

        FilterChips(state = state, vm = vm)
    }
}

/**
 * 搜索入口（对齐 Web 首页 `pages/Home.tsx:133-144`）：
 * `rounded-2xl` 16dp、`bg-ink-900/70`、`ring-1 ring-ink-800/70`、`shadow-soft`、
 * `px-4 py-3 text-sm text-ink-500`、图标 16dp、间距 10dp。
 *
 * **它是个跳转按钮，不是输入框** —— Web 上这个元素是 `<Link to="/search">`，
 * 点开进独立的搜索页。之前 Android 没有第二个路由，只好把它做成真输入框就地搜索，
 * 于是首页既要显示「全部漫画」又要显示「搜出来的结果」，两套状态混在一起。
 * 现在搜索页独立了，这里回归它本来的语义：点一下，跳到搜索页。
 */
@Composable
private fun SearchEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.20f),
            )
            .clip(shape)
            .background(ink.ink900.copy(alpha = 0.70f))
            .border(1.dp, ink.ink800.copy(alpha = 0.70f), shape)
            .pressClickable(onClickLabel = "搜索漫画", pressedScale = 0.99f, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = ink.ink500,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text("搜索漫画 / 作者", fontSize = 14.sp, color = ink.ink500, maxLines = 1)
        Spacer(Modifier.weight(1f))
        // Web 在 ≥sm 才显示右侧那个「搜索」小徽章（`hidden sm:block`）——
        // 手机上它被隐藏，这里也就只在宽屏（≥600dp）出现
        if (LocalConfiguration.current.screenWidthDp >= 600) {
            Text(
                "搜索",
                fontSize = 10.sp,
                color = ink.ink500,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ink.ink800.copy(alpha = 0.70f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 扫描横幅的订阅边界。
 *
 * 扫描进行中 `done / total` 每 800ms 变一次（[HomeViewModel.watchScan]）。这个订阅
 * 如果放在 `HomeScreen` 顶层，每次进度更新都会把整个首页拖去重组 —— 30 个筛选 chip、
 * 两个 LazyRow、以及所有可见卡片。收进这一层之后，重组的只有它自己和 [ScanBanner]。
 *
 * 这也是把 `scan` 从 `HomeUiState` 里拆出来的原因：同一个 state 里的任何字段变化
 * 都会让读这个 state 的 composable 全部失效。
 */
@Composable
private fun ScanBannerSlot(
    vm: HomeViewModel,
    onOpenLibraries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val running = scan?.takeIf { it.running }
    /*
     * 展开 / 收起 300ms（Web `pages/Home.tsx:160-166`：`grid-template-rows 0fr↔1fr`
     * + `transition 300ms ease-out-quint`）。
     *
     * 不能直接 `running?.let { … }` 挂摘：扫描开始与结束时横幅是整块约 50dp 的高度突变，
     * 下面的「继续阅读」和筛选 chips 会跟着跳一下。Web 那条 grid 过渡就是为了这个。
     */
    AnimatedVisibility(
        visible = running != null,
        enter = expandVertically(animationSpec = tween(300, easing = EaseOutQuint)),
        exit = shrinkVertically(animationSpec = tween(300, easing = EaseOutQuint)),
    ) {
        // 收起动画期间 scan 可能已经变成 null（扫描结束）：留最后一份继续渲染，
        // 否则横幅会在高度还没收完时就先「空掉」，看起来像闪了一下
        val last = remember { arrayOfNulls<ScanStatusDto>(1) }
        if (running != null) last[0] = running
        last[0]?.let { ScanBanner(scan = it, onOpenLibraries = onOpenLibraries, modifier = modifier) }
    }
}

/**
 * 空列表的两种含义：真的没有书 / 正在扫描。
 *
 * 只有这一支需要读扫描状态（Web `pages/Home.tsx:349-357` 同款区分）：空库 + 正在扫描
 * 不能说「还没有漫画」，那会被读成「扫描没生效、导入失败」。
 *
 * 带筛选时的空结果不读扫描 —— 那时用户已经筛掉了全部内容，跟扫不扫没关系。
 */
@Composable
private fun EmptyOrScanning(
    filtered: Boolean,
    onClearFilters: () -> Unit,
    onOpenLibraries: () -> Unit,
    vm: HomeViewModel,
) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val current = scan
    if (!filtered && current != null && current.running) {
        ScanningEmpty(current)
        return
    }
    EmptyView(
        title = if (filtered) "没有符合条件的漫画" else "还没有漫画",
        hint = if (filtered) {
            "换个筛选条件试试"
        } else {
            // Web：到「漫画库」添加目录并扫描 —— Android 的管理面在网页端
            "到「漫画库」添加目录并扫描，或换个筛选条件试试"
        },
        // Web 空库态有「去添加 →」（跳 /libraries）。Android 的漫画库页虽然只能扫描
        // 不能新建，但那里把「怎么加库」写清楚了，这个入口仍然该有。
        actionLabel = if (filtered) "清除筛选" else "去添加",
        actionArrow = !filtered, // 只有「去添加 →」带箭头，「清除筛选」是就地操作
        // 筛选后为空用「搜索空」插画，真正的空库用「空」插画 —— 与 Web 同一套区分
        mascot = if (filtered) MascotKind.SEARCH else MascotKind.EMPTY,
        onAction = if (filtered) onClearFilters else onOpenLibraries,
    )
}

/**
 * 扫描进度横幅（对齐 Web `pages/Home.tsx:170-199`）。
 *
 * 配色刻意只让图标/进度条带品牌色，文字走 ink 阶：brand-300/400 在浅色主题下是
 * 「浅橙压浅橙」，对比度不够；ink 阶是反相映射的，深浅两套主题都稳 —— Web 的注释
 * 写了同一条论证，别改成 brand-300。
 *
 * Web 右侧还有一个「查看」链接跳去漫画库页（`text-ink-400 underline underline-offset-2`）。
 * Android 现在也有漫画库页了（只读），所以那个链接能真正落地，不再是摆设。
 */
@Composable
private fun ScanBanner(
    scan: ScanStatusDto,
    onOpenLibraries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalBrandColors.current
    val ink = LocalInk.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brand.brand500.copy(alpha = 0.10f))
            .border(1.dp, brand.brand500.copy(alpha = 0.25f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.5.dp,
                color = brand.brand500,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "正在扫描漫画库",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ink.ink200,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (scan.percent > 0) "已完成 ${scan.done} / ${scan.total}" else scan.current,
                fontSize = 11.sp,
                color = ink.ink500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "查看",
                fontSize = 12.sp,
                color = ink.ink400,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .pressClickable(onClickLabel = "查看扫描详情", onClick = onOpenLibraries)
                    .padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
        // 进度条宽度 500ms 过渡（Web：`transition-[width] duration-500 ease-out-quint`）。
        // 扫描状态每 800ms 才更新一次，不补间的话每次都是硬跳 5~10%
        val fill by animateFloatAsState(
            targetValue = (scan.percent / 100f).coerceIn(0f, 1f),
            animationSpec = tween(500, easing = EaseOutQuint),
            label = "scanBar",
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(brand.brand500.copy(alpha = 0.15f))) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .fillMaxHeight()
                    .background(brand.brand500),
            )
        }
    }
}

/** 空库 + 正在扫描（Web `pages/Home.tsx:349-357`）：把「没有」换成「还在扫」 */
@Composable
private fun ScanningEmpty(scan: ScanStatusDto) {
    val ink = LocalInk.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = accentTextColor(),
        )
        Spacer(Modifier.height(12.dp))
        Text("正在扫描漫画库", fontSize = 14.sp, color = ink.ink300)
        Spacer(Modifier.height(8.dp))
        Text(
            if (scan.total > 0) {
                "已完成 ${scan.done} / ${scan.total}，扫到的漫画会自动出现"
            } else {
                "扫到的漫画会自动出现"
            },
            fontSize = 12.sp,
            color = ink.ink500,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 顶部「继续阅读」横条：直接进阅读器（保存的章与页），不给详情入口——
 * 想看简介可以从下面的网格点进详情，横条的唯一用途是「接着上次读」。
 */
@Composable
private fun ContinueReadingRow(
    items: List<HistoryItemDto>,
    coverUrl: (String) -> String?,
    onRead: (comicId: String, chapterId: String, page: Int) -> Unit,
) {
    val ink = LocalInk.current
    // 整页滚动后这一块是滚动容器的 item：左右内距由容器的 contentPadding 统一给，
    // 这里再补一份就会比网格多出 16dp（Web 上它们共用 <main> 的 px-3）
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)) {
        Row(
            Modifier.padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Web: <History size={15} className="text-brand-500/80" />
            HistoryGlyph(
                size = 15.dp,
                color = LocalBrandColors.current.brand500.copy(alpha = 0.8f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "继续阅读",
                fontSize = 14.sp, // Web text-sm
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.35).sp, // Web tracking-tight（-0.025em × 14px）
                color = ink.ink300,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { // Web gap-3
            items(items, key = { it.id }) { item ->
                ContinueCard(
                    item = item,
                    coverUrl = coverUrl(item.id),
                    onClick = { onRead(item.id, item.chapterId, item.pageIndex) },
                )
            }
        }
    }
}

/**
 * 横滑卡片（对齐 Web `pages/Home.tsx:231-261`）：
 * 宽 `w-28` = 112dp、标题 `text-xs font-medium` + `mt-1.5`、副标题 `text-[11px] text-ink-500`、
 * 封面底部一道 `from-black/40` 的 40dp 渐变压暗（让进度条在浅色封面上也看得见）。
 */
@Composable
private fun ContinueCard(
    item: HistoryItemDto,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    val ink = LocalInk.current
    val percent = item.progress?.percent ?: 0.0
    Column(
        modifier = Modifier
            .width(112.dp)
            .pressClickable(onClick = onClick),
    ) {
        // 压暗渐变与进度条都走 ComicCover 的 overlay 槽：铺在封面自己的裁切层里，
        // 外面再包一层 clip 会把封面的阴影一起切掉（见 ComicCover 的注释）
        ComicCover(coverUrl, item.title, Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f)),
                        ),
                    ),
            )
            ProgressBarStatic(
                percent.toFloat().coerceIn(0f, 100f),
                Modifier.align(Alignment.BottomStart),
            )
        }
        Spacer(Modifier.height(6.dp)) // Web mt-1.5
        Text(
            item.title,
            fontSize = 12.sp, // Web text-xs
            fontWeight = FontWeight.Medium,
            color = ink.ink100,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${item.chapterIndex + 1} 章 · 第 ${item.pageIndex + 1} 页",
            fontSize = 11.sp,
            color = ink.ink500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun hasFilters(state: HomeUiState): Boolean =
    state.filter.status != null || state.filter.format != null || state.filter.tag != null

private val SORT_OPTIONS = listOf(
    "addedAt" to "最近添加",
    "title" to "名称",
    "lastReadAt" to "最近阅读",
)

/**
 * 排序行（对齐 Web `pages/Home.tsx:308-324`）：
 * 左侧「全部漫画 (509)」，右侧三个 11sp 文字按钮，选中项 `text-brand-400`。
 * 早先 Android 是下拉菜单 —— 手机上多一步点开，且和 Web 的交互形态不一致。
 */
@Composable
private fun SortRow(sort: String, total: Int, showTotal: Boolean, onSelect: (String) -> Unit) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "全部漫画",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink.ink300,
            )
            if (showTotal) {
                // 加载失败时不显示 (0)：那会被读成「库里一本书都没有」
                Spacer(Modifier.width(4.dp))
                Text("($total)", fontSize = 14.sp, color = ink.ink500)
            }
        }
        Spacer(Modifier.weight(1f))
        SORT_OPTIONS.forEach { (value, label) ->
            Text(
                label,
                fontSize = 11.sp,
                color = if (value == sort) accentTextColor() else ink.ink500,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .pressClickable(onClickLabel = "按$label 排序") {
                        if (value != sort) onSelect(value)
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun FilterChips(state: HomeUiState, vm: HomeViewModel) {
    val facets = state.facets
    val formatValues = remember(facets) { facets?.formats?.map { it.value }?.toSet().orEmpty() }
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val statuses = listOf("unread" to "未读", "reading" to "在读", "finished" to "读完")
            items(statuses.size) { i ->
                val (value, label) = statuses[i]
                BrandChip(
                    text = label,
                    selected = state.filter.status == value,
                    onClick = { vm.toggleStatus(value) },
                )
            }
            items(facets?.formats?.size ?: 0) { i ->
                val f = facets!!.formats[i]
                BrandChip(
                    text = "${f.value.uppercase()} ${f.count}",
                    selected = state.filter.format == f.value,
                    onClick = { vm.toggleFormat(f.value) },
                )
            }
        }
        Spacer(Modifier.height(8.dp)) // Web gap-2
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tags = facets?.tags?.filter { it.value !in formatValues }.orEmpty()
            items(tags.size) { i ->
                val tag = tags[i]
                BrandChip(
                    text = "${tag.value} ${tag.count}",
                    selected = state.filter.tag == tag.value,
                    onClick = { vm.toggleTag(tag.value) },
                )
            }
            // Web 的「重置」常驻在这一行末尾（`ml-auto` 在溢出容器里等价于「排在最后」），
            // 点了清空全部筛选（连排序也回默认） —— 与 Web 的 setParams(new URLSearchParams()) 同语义
            item {
                BrandChip(
                    text = "重置",
                    selected = false,
                    onClick = { vm.clearFilters() },
                )
            }
        }
        SortRow(
            sort = state.filter.sort,
            total = state.total,
            showTotal = state.error == null,
            onSelect = vm::setSort,
        )
    }
}

/**
 * 列表底部（无限滚动）：三态照 Web `ComicListFooter` ——
 * 加载中转圈 + 「正在加载更多…」（`text-xs text-ink-500`）、
 * 续拉失败是**文字**按钮（`text-xs text-ink-400`，不是实心按钮）、
 * 到底了「已经到底了 · 共 N 部」（`text-[11px] text-ink-600`）。
 */
@Composable
private fun ListFooter(state: HomeUiState, vm: HomeViewModel) {
    val ink = LocalInk.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.loadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    Modifier.size(14.dp),
                    strokeWidth = 1.6.dp,
                    color = ink.ink500,
                )
                Spacer(Modifier.width(8.dp))
                Text("正在加载更多…", fontSize = 12.sp, color = ink.ink500)
            }
            state.loadMoreFailed -> Text(
                "加载更多失败 · 点击重试",
                fontSize = 12.sp,
                color = ink.ink400,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .pressClickable(onClick = vm::retryLoadMore)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            state.items.size >= 36 -> Text(
                "已经到底了 · 共 ${state.total} 部",
                fontSize = 11.sp,
                color = ink.ink600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
