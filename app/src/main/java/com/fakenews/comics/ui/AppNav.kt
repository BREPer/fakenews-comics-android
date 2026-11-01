package com.fakenews.comics.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fakenews.comics.ui.boot.BootScreen
import com.fakenews.comics.ui.common.AmbientBackdrop
import com.fakenews.comics.ui.common.ContentMaxWidth
import com.fakenews.comics.ui.common.EaseOutQuint
import com.fakenews.comics.ui.common.LocalToast
import com.fakenews.comics.ui.common.ToastCenter
import com.fakenews.comics.ui.common.ToastHost
import com.fakenews.comics.ui.detail.DetailScreen
import com.fakenews.comics.ui.home.HomeScreen
import com.fakenews.comics.ui.home.HomeViewModel
import com.fakenews.comics.ui.libraries.LibrariesScreen
import com.fakenews.comics.ui.libraries.LibrariesViewModel
import com.fakenews.comics.ui.login.LoginScreen
import com.fakenews.comics.ui.reader.ReaderScreen
import com.fakenews.comics.ui.search.SearchScreen
import com.fakenews.comics.ui.search.SearchViewModel
import com.fakenews.comics.ui.settings.SettingsScreen
import com.fakenews.comics.ui.settings.SettingsViewModel
import com.fakenews.comics.ui.setup.SetupScreen
import com.fakenews.comics.ui.shelf.FavoritesViewModel
import com.fakenews.comics.ui.shelf.HistoryViewModel
import com.fakenews.comics.ui.shelf.ShelfScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

private object Routes {
    const val BOOT = "boot"
    const val SETUP = "setup"
    const val LOGIN = "login"
    const val MAIN = "main"
    const val DETAIL = "detail/{comicId}"
    const val READER = "read/{comicId}/{chapterId}/{page}"

    fun detail(comicId: String) = "detail/${Uri.encode(comicId)}"

    /** chapterId 形如 `abc#0`：不 encode 的话 `#` 会被当成 URI fragment 截掉。 */
    fun read(comicId: String, chapterId: String, page: Int) =
        "read/${Uri.encode(comicId)}/${Uri.encode(chapterId)}/$page"
}

/**
 * 底部五个 tab —— 与 Web `AppShell.tsx` 的 `NAV` 常量**逐项同名同序**：
 * 首页 `/` · 书架 `/shelf` · 搜索 `/search` · 漫画库 `/libraries` · 设置 `/settings`。
 *
 * 之前 Android 是「书架 / 收藏 / 记录 / 我的」四项，与 Web 只重合一项：
 * 那个「书架」装的其实是 Web 首页的内容，而 Web 的书架页（收藏 + 记录两个 section）
 * 被拆成了两个 tab，搜索与漫画库整项缺失。这里的对齐是导航骨架级别的修正。
 *
 * BottomBar.kt 也要用（tab 选中态与切换），所以是 internal。
 */
internal object Tabs {
    const val HOME = "home"
    const val SHELF = "shelf"
    const val SEARCH = "search"
    const val LIBRARIES = "libraries"
    const val SETTINGS = "settings"
}

/*
 * 路由切换：**只淡入，不做位移**（对齐 Web .anim-route，200ms / ease-out-quint）。
 * navigation-compose 默认是 700ms 的淡入淡出，在「书架 → 详情」这种大容器切换上
 * 明显发钝；Web 当年也把 rise（位移）去掉了 —— 位移在大容器上会被读成「内容错位」。
 * 淡入淡出不读 scope 状态，四个方向共用同一段时长与曲线即可。
 *
 * 详情页也走这一条：它先前有自己的上浮（屏高 1/48）+ 封面共享元素，
 * 2026-10-30 用户否掉、要求重新构想，两者都已撤掉（不要照着旧提交恢复）。
 * 现在只有阅读器单独覆盖了转场，见下面 ReaderRiseDivisor。
 */
private val RouteFadeSpec = tween<Float>(durationMillis = 200, easing = EaseOutQuint)

/**
 * 「进入下一层」的转场，**目前只有阅读器用**（详情页已撤）。
 *
 * tab 之间是**并列**关系，只该淡入淡出；但阅读器是叠在当前内容之上的一层新上下文，
 * 纯淡入会丢掉方位感 —— 从书架直接跳进阅读器时，前后两屏都是封面和图片，
 * 光靠透明度分不清自己是在往里走还是往回退。
 *
 * 所以给一点点上浮。**只给一点点**：大容器上的位移一旦明显，就会被读成「内容错位」
 * 而不是「层级推进」—— Web 当年把 rise 砍掉就是这个原因。曲线用 EaseOutQuint
 * （起步快、收尾稳），时长比淡入长 60ms，让位移先于透明度结束，落位时不会觉得"还在飘"。
 */
private const val RouteRiseMs = 260

/** 位移的 spec 是 `IntOffset`（`slideInVertically` 的 offset lambda 返回像素），不是 Float */
private val RouteRiseSpec = tween<IntOffset>(durationMillis = RouteRiseMs, easing = EaseOutQuint)

/** 阅读器是层级最深的一屏（全屏、无底栏），上浮给到屏高 ÷ 36 */
private const val ReaderRiseDivisor = 36

@Composable
fun AppNav(vm: AppViewModel = hiltViewModel()) {
    val nav = rememberNavController()

    LaunchedEffect(Unit) {
        vm.unauthorized.collect {
            if (nav.currentDestination?.route != Routes.LOGIN) {
                nav.resetTo(Routes.LOGIN)
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.BOOT,
        enterTransition = { fadeIn(animationSpec = RouteFadeSpec) },
        exitTransition = { fadeOut(animationSpec = RouteFadeSpec) },
        popEnterTransition = { fadeIn(animationSpec = RouteFadeSpec) },
        popExitTransition = { fadeOut(animationSpec = RouteFadeSpec) },
    ) {
        composable(Routes.BOOT) {
            BootScreen(
                onNeedSetup = { nav.resetTo(Routes.SETUP) },
                onNeedLogin = { nav.resetTo(Routes.LOGIN) },
                onReady = { nav.resetTo(Routes.MAIN) },
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                onNeedLogin = { nav.resetTo(Routes.LOGIN) },
                onReady = { nav.resetTo(Routes.MAIN) },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = { nav.resetTo(Routes.MAIN) },
                onChangeServer = { nav.resetTo(Routes.SETUP) },
            )
        }
        composable(Routes.MAIN) {
            MainShell(
                onOpenComic = { nav.navigate(Routes.detail(it)) },
                // 书架「继续阅读」与记录行都是直达阅读器：省掉「先详情再点开始读」这一跳
                onOpenReader = { comicId, chapterId, page ->
                    nav.navigate(Routes.read(comicId, chapterId, page))
                },
                onChangeServer = { nav.resetTo(Routes.SETUP) },
                onLoggedOut = { nav.resetTo(Routes.LOGIN) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("comicId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("comicId").orEmpty()
            DetailRoute(
                comicId = id,
                onBack = { nav.popBackStack() },
                onOpenReader = { chapterId, page ->
                    nav.navigate(Routes.read(id, chapterId, page))
                },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument("comicId") { type = NavType.StringType },
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("page") { type = NavType.IntType; defaultValue = -1 },
            ),
            /*
             * 阅读器是层级最深的一屏（全屏、无底栏），位移给得比详情页明显 ——
             * 从书架「继续阅读」直达阅读器时，用户跨过了整整一层界面，
             * 如果还是 200ms 纯淡入，会像"换了个 app"。
             */
            enterTransition = {
                fadeIn(RouteFadeSpec) +
                    slideInVertically(RouteRiseSpec) { it / ReaderRiseDivisor }
            },
            exitTransition = { fadeOut(RouteFadeSpec) },
            popEnterTransition = { fadeIn(RouteFadeSpec) },
            popExitTransition = {
                fadeOut(RouteFadeSpec) +
                    slideOutVertically(RouteRiseSpec) { it / ReaderRiseDivisor }
            },
        ) {
            ReaderScreen(onBack = { nav.popBackStack() })
        }
    }
}

/** 底部五个 tab：各自保留滚动位置与分页状态，切回来不会回到第一页。 */
@Composable
private fun MainShell(
    onOpenComic: (String) -> Unit,
    onOpenReader: (comicId: String, chapterId: String, page: Int) -> Unit,
    onChangeServer: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val toast = remember { ToastCenter() }
    val tabNav = rememberNavController()
    // Haze 的源/子两端共用一个状态：源是滚动内容，子是底栏（见 Web backdrop-blur-md）
    val hazeState = remember { HazeState() }

    /*
     * 五个 tab 的 VM 全部挂到 **MAIN 这个 NavBackStackEntry** 上，而不是各 tab 自己的 entry。
     *
     * 为什么（2026-10-28 实测定位「都是本地资源，切底栏还有加载中」）：
     * 切 tab 时数据其实不重拉 —— 服务端本地响应只有 1-3ms，且 VM 靠着 switchTo 的
     * saveState/restoreState 也能活过弹栈（日志实测：切来切去每个 VM 只 init 一次）。
     * 真正可见的「加载中」是每个 tab **当日首次进入**的那一下：VM 懒创建 → init 里发请求 →
     * 期间出 ~130ms 骨架，数据回来后内容再跑 340ms 错峰入场渐显。首访完再切就瞬间到位。
     *
     * 把 VM 提升到 MAIN entry（活到登出/换服务器，resetTo 才弹它）有两个好处：
     *   1) 在 MainShell 里一次性创建 = **预热**：首页渲染的同时，其余四个 tab 的数据已在
     *      后台拉好（实测 5 个 VM 在 157ms 内全部建完，与首页拉取并发，不拖首屏）。
     *      用户点过去时数据已在，首次进入也不再出骨架。
     *   2) VM 的存活从「依赖 saveState/restoreState 的语义」变成**结构性保证** ——
     *       不靠导航库的行为细节，MAIN 在则 VM 在。搜索词/结果因此与 Web 一样
     *       跨 tab 保留（Web 的查询在 URL 里，useSearchParams）。
     *
     * 进程死亡后 NavHost 状态恢复会重建 entry，VM 重来一遍，与今天行为一致。
     */
    val homeVm: HomeViewModel = hiltViewModel()
    val favVm: FavoritesViewModel = hiltViewModel()
    val histVm: HistoryViewModel = hiltViewModel()
    val searchVm: SearchViewModel = hiltViewModel()
    val libVm: LibrariesViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()

    CompositionLocalProvider(LocalToast provides toast) {
        Scaffold(
            // 提示浮在底栏之上（Web：`bottom-[4.75rem]` + 安全区）。
            // 用 Scaffold 的 snackbarHost 槽位拿到的就是这个位置，不用自己算
            snackbarHost = {
                ToastHost(toast, Modifier.padding(bottom = 8.dp), hazeState = hazeState)
            },
            bottomBar = { MainBottomBar(tabNav, hazeState) },
            // 容器透明：页面底色由 AmbientBackdrop 统一铺，Scaffold 的底色会盖住它
            containerColor = Color.Transparent,
        ) { inner ->
            // 只让出状态栏：底栏浮在内容之上，列表从栏下滚过（对齐 Web 的 fixed nav）。
            // 底部安全区 + 栏高通过 LocalBottomBarInset 交给各页做 content padding。
            //
            // 2026-10-28 晚定稿（走过一次弯路，记在这里）：
            // 底栏是「纯模糊、零底色」。曾有一版让内容提前一个 navInset 结束，理由是
            // 「手势区那 24dp 漏出了没模糊的列表」。修法本身有效，但副作用是那 24dp 里
            // 既没有内容可糊、又不铺底色 → 什么都不画，视觉上成了「栏只糊了一半」的洞。
            // 正解是**让玻璃铺到屏幕底**：内容照常流到底，毛玻璃连手势区一起糊，
            // 于是那里显示的是"模糊的内容"而不是空洞。栏高（56）与留白（10dp 缝）不变，
            // 差别只在 inset 不再从上往下啃内容区。
            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            CompositionLocalProvider(
                // 减掉手势区（栏自己已经用 navigationBarsPadding 顶开了），再补上 Web 那道 10dp 缝。
                LocalBottomBarInset provides (
                    inner.calculateBottomPadding() - navInset + BottomBarContentGap
                    ).coerceAtLeast(0.dp),
                // 栏顶边到屏幕底边（含手势区）：浮层（回顶按钮）悬在栏之上，量的是到屏幕底边
                LocalBottomBarTopInset provides inner.calculateBottomPadding(),
            ) {
                AmbientBackdrop(
                    Modifier
                        .padding(top = inner.calculateTopPadding())
                        .haze(hazeState),
                ) {
                    /*
                     * 宽屏（平板 / 展开态折叠屏）：内容收到 [ContentMaxWidth] 并居中。
                     *
                     * 背景（AmbientBackdrop）与底栏仍然铺满，只有内容不跟着屏幕一起变长 ——
                     * 否则一行文字横跨 1000dp、一张封面被拉到 250dp 宽，手机上的阅读节奏
                     * 整个没了。haze 的源仍是这层背景，不受影响。
                     */
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.fillMaxHeight().widthIn(max = ContentMaxWidth)) {
                            NavHost(
                                navController = tabNav,
                                startDestination = Tabs.HOME,
                                modifier = Modifier.fillMaxSize(),
                                enterTransition = { fadeIn(animationSpec = RouteFadeSpec) },
                                exitTransition = { fadeOut(animationSpec = RouteFadeSpec) },
                                popEnterTransition = { fadeIn(animationSpec = RouteFadeSpec) },
                                popExitTransition = { fadeOut(animationSpec = RouteFadeSpec) },
                            ) {
                                composable(Tabs.HOME) {
                                    HomeScreen(
                                        onOpenComic = onOpenComic,
                                        onOpenReader = onOpenReader,
                                        onOpenSearch = { tabNav.switchTo(Tabs.SEARCH) },
                                        onOpenLibraries = { tabNav.switchTo(Tabs.LIBRARIES) },
                                        vm = homeVm,
                                    )
                                }
                                composable(Tabs.SHELF) {
                                    ShelfScreen(
                                        onOpenComic = onOpenComic,
                                        onOpenReader = onOpenReader,
                                        // 两个区块的空态都指向首页（Web 的 `to="/"`）
                                        onBrowse = { tabNav.switchTo(Tabs.HOME) },
                                        favVm = favVm,
                                        histVm = histVm,
                                    )
                                }
                                composable(Tabs.SEARCH) {
                                    SearchScreen(onOpenComic = onOpenComic, vm = searchVm)
                                }
                                composable(Tabs.LIBRARIES) {
                                    LibrariesScreen(vm = libVm)
                                }
                                composable(Tabs.SETTINGS) {
                                    SettingsScreen(
                                        onChangeServer = onChangeServer,
                                        onLoggedOut = onLoggedOut,
                                        vm = settingsVm,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 详情页不挂底栏，但要有自己的提示宿主（收藏/重置/重扫的回执）。 */
@Composable
private fun DetailRoute(
    comicId: String,
    onBack: () -> Unit,
    onOpenReader: (chapterId: String, page: Int) -> Unit,
) {
    val toast = remember { ToastCenter() }
    // 详情页自己一层采样源：toast 要能糊到身下的详情内容（底栏不在这一屏）
    val hazeState = remember { HazeState() }
    CompositionLocalProvider(LocalToast provides toast) {
        Scaffold(
            snackbarHost = {
                ToastHost(toast, Modifier.padding(bottom = 8.dp), hazeState = hazeState)
            },
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            /*
             * 显式给内容色：`containerColor = Transparent` 时 `contentColorFor()` 返回
             * Unspecified，Surface 不再覆写 LocalContentColor，任何没写 color 的 Text
             * 都会落到默认的**纯黑**（深色主题下就是黑字压黑底）。详情页大标题踩过这个坑
             * （2026-10-30），它已在自己的 Text 上显式给 ink100，这里再加一道宿主级兜底。
             */
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) { inner ->
            /*
             * 只让出**底部**安全区：顶部的状态栏不再由这里整页让开，否则详情页的
             * 环境光只能从状态栏下沿起画，光晕上沿会切出一条横贯整屏的硬边。
             * 顶栏的避让交给 DetailScreen 自己做（windowInsetsPadding(statusBars)），
             * 于是背景与光晕能一路铺到屏幕顶端，与 Web 的 `.page-ambient { top: 0 }` 一致。
             */
            AmbientBackdrop(
                Modifier
                    .padding(bottom = inner.calculateBottomPadding())
                    .haze(hazeState),
            ) {
                // 同主区：宽屏给详情页一条 1080 的阅读边界（封面与信息不再被拉到极宽）
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.fillMaxHeight().widthIn(max = ContentMaxWidth)) {
                        DetailScreen(
                            comicId = comicId,
                            onBack = onBack,
                            onOpenReader = onOpenReader,
                        )
                    }
                }
            }
        }
    }
}

/** 启动/登录/换服务器都重开栈，避免返回键退回失效的旧连接页。 */
private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

/** tab 间切换（保留滚动/分页状态）；AppNav.kt 与 BottomBar.kt 共用。 */
internal fun NavHostController.switchTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
