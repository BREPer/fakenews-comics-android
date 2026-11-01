package com.fakenews.comics.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.fakenews.comics.ui.common.BackToTopButton
import com.fakenews.comics.ui.common.rememberBackToTopVisible
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakenews.comics.ui.comic.ComicCard
import com.fakenews.comics.ui.common.ComicGridSkeleton
import com.fakenews.comics.ui.common.comicGridColumns
import com.fakenews.comics.ui.common.CloseGlyph
import com.fakenews.comics.ui.common.EmptyView
import com.fakenews.comics.ui.common.ErrorView
import com.fakenews.comics.ui.common.MascotKind
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.LocalBottomBarInset
import com.fakenews.comics.ui.theme.AppFontFamily
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 搜索页（Web `pages/Search.tsx`）。
 *
 * Web 上它是底栏的第三项 `/search`，与首页是两个页面：首页那个「像输入框的按钮」
 * 点过来，落地在这里。之前 Android 没有独立搜索页，把输入框塞在首页顶部就地搜索，
 * 于是「全部漫画」和「搜索结果」共用一份列表状态 —— 搜完再清空，首页的筛选与
 * 滚动位置全没了。
 *
 * 显示优先级（照抄 Web 的 when 链，顺序不能调）：
 *   1. 没关键词 / 慢查询阈值内还没结果 → 「输入关键词开始搜索」（这一档是**防闪**用的：
 *      快搜索走完之前它一直是它，视觉上就是「什么都没闪，直接变成结果」）；
 *   2. 有结果 → 结果网格；
 *   3. 出错 → 错误态；
 *   4. 有关键词但没结果且请求在飞 → 骨架屏（只在慢查询时才走到这里）；
 *   5. 否则 → 「没有找到「q」」。
 */
@Composable
fun SearchScreen(
    onOpenComic: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    // 滚过 480dp（Web 同款阈值）出现回顶按钮；首页/书架/漫画库是同一个组件
    val showBackToTop = rememberBackToTopVisible(gridState)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val lastVisible by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    LaunchedEffect(lastVisible, state.items.size, state.hasMore) {
        if (state.hasMore && lastVisible >= state.items.size - 6) vm.loadMore()
    }

    Column(modifier.fillMaxSize()) {
        SearchField(
            value = state.text,
            fetching = state.fetching,
            onValueChange = vm::onTextChange,
            onSubmit = {
                vm.submitNow()
                keyboard?.hide()
            },
            onClear = vm::clear,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )

        Box(Modifier.fillMaxSize()) {
            when {
                state.q.isBlank() || (state.pendingFirst && !state.slow) -> EmptyView(
                    title = "输入关键词开始搜索",
                    hint = "支持漫画名、作者、原始文件名模糊匹配",
                    mascot = MascotKind.SEARCH,
                    // Web 的这块提示紧跟输入框（内容高度 + py-20），不铺满剩余屏幕
                    fillHeight = false,
                )
                state.items.isNotEmpty() -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cols = comicGridColumns(maxWidth)
                    LazyVerticalGrid(
                        columns = cols,
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 24.dp + LocalBottomBarInset.current,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ResultCount(total = state.total, shown = state.items.size)
                        }
                        itemsIndexed(state.items, key = { _, c -> c.id }) { index, comic ->
                            ComicCard(
                                comic = comic,
                                coverUrl = vm.coverUrl(comic.id),
                                onClick = { onOpenComic(comic.id) },
                                index = index,
                            )
                        }
                        // Web `ComicListFooter`：`hasMore || loaded < firstPageSize` 时整块返回 null。
                        // 只看 `!hasMore` 会在「结果本来就不满一页」时插一条 48dp 的空页脚。
                        if (state.loadingMore ||
                            state.loadMoreFailed ||
                            (!state.hasMore && state.items.size >= PAGE_SIZE)
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ListFooter(state = state, onRetry = vm::retryLoadMore)
                            }
                        }
                    }
                }
                state.error != null -> ErrorView(
                    title = "搜索失败",
                    message = state.error,
                    hint = "服务可能在忙（扫描大库），稍后重试",
                    onRetry = vm::retry,
                    fillHeight = false,
                )
                state.pendingFirst -> ComicGridSkeleton()
                else -> EmptyView(
                    title = "没有找到「${state.q}」",
                    hint = "换个关键词，或检查漫画库是否已扫描",
                    mascot = MascotKind.SEARCH,
                    fillHeight = false,
                )
            }
            // 回顶按钮只盖滚动区，不进滚动容器：否则它会跟着内容一起滑走。
            // RefreshBox / Box 的 content 都是 BoxScope，直接 align 就行。
            BackToTopButton(
                visible = showBackToTop,
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/**
 * 搜索输入框（Web `input.input pl-9 pr-9`）：
 * `rounded-xl` 12dp、`bg-ink-900`、`ring-1 ring-ink-800`、内距 12dp，min-height 44dp。
 *
 * 转圈只在 [fetching] 时出现（不含防抖那 300ms）：每敲一个字都闪一下会被当成故障。
 * 有清除按钮时转圈左移让位（Web 的 `right-3` / `right-8` 两个位置）。
 */
@Composable
private fun SearchField(
    value: String,
    fetching: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    // 与 Web `.input` 的 `transition-[box-shadow,border-color] duration-150` 对齐：
    // 聚焦瞬间从 1dp ink-800 跳到 2dp 品牌色太生硬，150ms 渐变才像「亮起来」
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        animationSpec = tween(150),
        label = "searchRingWidth",
    )
    val ringColor by animateColorAsState(
        targetValue = if (focused) brand.brand600.copy(alpha = 0.8f) else ink.ink800,
        animationSpec = tween(150),
        label = "searchRingColor",
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = ink.ink100, fontFamily = AppFontFamily),
        cursorBrush = SolidColor(brand.brand500),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(shape)
                    .background(ink.ink900)
                    // Web `.input`：`ring-1 ring-ink-800 focus:ring-2 focus:ring-brand-600/80`
                    .border(width = ringWidth, color = ringColor, shape = shape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = ink.ink500,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "搜索漫画名 / 作者 / 文件名",
                            fontSize = 14.sp,
                            color = ink.ink500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (fetching) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 1.8.dp,
                        color = ink.ink500,
                    )
                }
                if (value.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .pressClickable(onClickLabel = "清空搜索", onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 自绘 lucide `X`：`Icons.Filled.Clear` 是「实心圆 + 白叉」，
                        // 和 Web 的两根细线不是一个东西
                        CloseGlyph(size = 15.dp, color = ink.ink500)
                    }
                }
            }
        },
    )
}

/** Web：`找到 {total} 部` + `（已显示 {shown} 部，继续下滑加载）` 两段，第二段 ink-600。 */
@Composable
private fun ResultCount(total: Int, shown: Int) {
    val ink = LocalInk.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("找到 $total 部", fontSize = 12.sp, color = ink.ink500)
        if (shown < total) {
            Text(
                "（已显示 $shown 部，继续下滑加载）",
                fontSize = 12.sp,
                color = ink.ink600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 列表底部三态（照 Web `ComicListFooter`）：
 * 加载中转圈 + 「正在加载更多…」、续拉失败是**文字**按钮、到底了「已经到底了 · 共 N 部」。
 */
@Composable
private fun ListFooter(state: SearchUiState, onRetry: () -> Unit) {
    val ink = LocalInk.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.loadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.6.dp, color = ink.ink500)
                Spacer(Modifier.width(8.dp))
                Text("正在加载更多…", fontSize = 12.sp, color = ink.ink500)
            }
            state.loadMoreFailed -> Text(
                "加载更多失败 · 点击重试",
                fontSize = 12.sp,
                color = ink.ink400,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .pressClickable(onClick = onRetry)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            /*
             * Web：`if (hasMore || loaded < firstPageSize) return null` —— 只搜出三五个结果时
             * 不说「已经到底了」，那句话是给「翻完一整页还想继续」的场景用的。
             */
            state.items.size >= PAGE_SIZE -> Text(
                "已经到底了 · 共 ${state.total} 部",
                fontSize = 11.sp,
                color = ink.ink600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
