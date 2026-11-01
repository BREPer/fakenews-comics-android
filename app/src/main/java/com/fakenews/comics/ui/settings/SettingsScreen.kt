package com.fakenews.comics.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakenews.comics.BuildConfig
import com.fakenews.comics.R
import com.fakenews.comics.data.session.Accent
import com.fakenews.comics.data.session.Appearance
import com.fakenews.comics.data.session.FitMode
import com.fakenews.comics.data.session.PageAnimation
import com.fakenews.comics.data.session.PageQuality
import com.fakenews.comics.data.session.ReadDir
import com.fakenews.comics.data.session.ReaderMode
import com.fakenews.comics.data.session.ReaderSettings
import com.fakenews.comics.data.session.ThemeMode
import com.fakenews.comics.ui.common.AccentSwatch
import com.fakenews.comics.ui.common.BrandButton
import com.fakenews.comics.ui.common.BrandChip
import com.fakenews.comics.ui.common.BrandSegmented
import com.fakenews.comics.ui.common.CardSurface
import com.fakenews.comics.ui.common.ErrorView
import com.fakenews.comics.ui.common.GhostButton
import com.fakenews.comics.ui.common.LoadingView
import com.fakenews.comics.ui.common.LocalToast
import com.fakenews.comics.ui.common.Mascot
import com.fakenews.comics.ui.common.MascotKind
import com.fakenews.comics.ui.common.ToastErrorRed
import com.fakenews.comics.ui.common.formatBytes
import com.fakenews.comics.ui.common.formatUptime
import com.fakenews.comics.ui.LocalBottomBarInset
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk
import com.fakenews.comics.ui.theme.accentTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 扫描进行中的轮询间隔（与首页横幅同值） */
private const val SCAN_ACTIVE_MS = 800L

/** 空闲时的轮询间隔（Web 同值） */
private const val SCAN_IDLE_MS = 15_000L

/** 版本不一致告警用的琥珀色（Web `text-amber-300`） */
private val AmberWarn = Color(0xFFFBBF24)

/**
 * 设置页（Web `pages/Settings.tsx`）。
 *
 * 六个区块与 Web 逐一同名同序：阅读偏好 / 主题 / 存储与扫描 / 数据备份 / 登录保护 / 关于。
 *
 * 与 Web 的差异只有一处，是平台事实：**「服务器」信息**（当前地址、连通状态、更换服务器）
 * 在 Web 上不存在 —— Web 与后端同源部署，而 Android 是独立客户端，必须能看能换。
 * 这部分并进「关于」区，不另开一个 Web 没有的区块。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onChangeServer: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val reader by vm.reader.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    LaunchedEffect(Unit) {
        vm.loggedOut.collect { onLoggedOut() }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> launch { toast?.show(msg.text, msg.kind) } }
    }
    // 扫描进度轮询：进行中 800ms、空闲 15s。随页面销毁自动停（VM 不管定时器）
    LaunchedEffect(state.scan?.running) {
        val running = state.scan?.running == true
        delay(if (running) SCAN_ACTIVE_MS else SCAN_IDLE_MS)
        vm.refreshScan()
    }

    // 备份文件读写走系统文件选择器：不需要任何存储权限，也不用自己拼路径
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importBackup) }

    /*
     * 首屏兜底，对齐 Web 的两个早返回：
     *   if (isError) return <ErrorState title="设置加载失败" hint="服务可能正在忙，稍后再试" />
     *   if (isLoading || !settings) return <Loading label="加载设置…" />
     *
     * 判据是 `!loaded`：**只兜首屏**。刷新时（loading 但加载过）保留已渲染的内容，
     * 否则每点一次「刷新」整页都会空一下 —— Web 的 isLoading 同样只在首屏为 true。
     */
    if (state.loading && !state.loaded) {
        LoadingView(label = "加载设置…")
        return
    }
    if (state.error != null && !state.loaded) {
        ErrorView(
            title = "设置加载失败",
            message = state.error,
            hint = "服务可能正在忙，稍后再试",
            onRetry = { vm.refresh() },
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                // 底栏浮在内容上，末尾要能滚到栏上方
                bottom = 12.dp + LocalBottomBarInset.current,
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "设置",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = LocalInk.current.ink100,
        )

        ReaderPrefsSection(reader = reader, onChange = vm::setReader)

        ThemeSection(
            appearance = appearance,
            onTheme = vm::setTheme,
            onAccent = vm::setAccent,
        )

        StorageSection(state = state, vm = vm)

        BackupSection(
            state = state,
            onExport = { exportLauncher.launch("fakenews-comics-backup-${todayStamp()}.json") },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )

        AuthSection(state = state, vm = vm)

        AboutSection(state = state)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton(
                text = "更换服务器",
                onClick = onChangeServer,
                modifier = Modifier.weight(1f),
            )
            BrandButton(
                text = if (state.loading) "读取中…" else "刷新",
                onClick = vm::refresh,
                modifier = Modifier.weight(1f),
                enabled = !state.loading,
            )
        }

        VersionFooter()
    }
}

/**
 * 阅读偏好（Web 的九行）。
 *
 * 「适应方式」有个联动：双页 / 卷轴模式下固定适应方式，只有单页模式能切 ——
 * 这条不出现在别处，hint 必须写清楚，否则用户会以为是分段选择器坏了。
 * 「预加载页数」是档位（关/1/3/5/8）而不是连续值，见 `ReaderSettings.PRELOAD_STEPS`。
 */
@Composable
private fun ReaderPrefsSection(
    reader: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
) {
    Section("阅读偏好") {
        SettingRow(label = "默认翻页模式") {
            BrandSegmented(
                options = listOf(
                    ReaderMode.WEBTOON to "卷轴",
                    ReaderMode.SINGLE to "单页",
                    ReaderMode.DOUBLE to "双页",
                    ReaderMode.SPLIT to "拆分",
                ),
                value = reader.mode,
                onChange = { v -> onChange { it.copy(mode = v) } },
            )
        }
        RowDivider()
        SettingRow(label = "阅读方向", hint = "日漫右翻 / 欧漫左翻") {
            BrandSegmented(
                options = listOf(ReadDir.RTL to "右翻", ReadDir.LTR to "左翻"),
                value = reader.readDir,
                onChange = { v -> onChange { it.copy(readDir = v) } },
            )
        }
        RowDivider()
        SettingRow(label = "默认画质") {
            BrandSegmented(
                options = listOf(
                    PageQuality.SMOOTH to "流畅",
                    PageQuality.STANDARD to "标准",
                    PageQuality.ORIGINAL to "原图",
                ),
                value = reader.quality,
                onChange = { v -> onChange { it.copy(quality = v) } },
            )
        }
        RowDivider()
        SettingRow(
            label = "适应方式",
            hint = if (reader.mode != ReaderMode.SINGLE) {
                "双页 / 卷轴模式下固定适应方式，仅单页模式可切换"
            } else {
                null
            },
        ) {
            BrandSegmented(
                options = listOf(
                    FitMode.WIDTH to "宽度",
                    FitMode.HEIGHT to "高度",
                    FitMode.CONTAIN to "完整",
                    FitMode.ORIGINAL to "原始",
                ),
                value = reader.fit,
                enabled = reader.mode == ReaderMode.SINGLE,
                onChange = { v -> onChange { it.copy(fit = v) } },
            )
        }
        RowDivider()
        SettingRow(label = "预加载页数", hint = "提前解码后续页面，翻页更跟手") {
            BrandSegmented(
                options = ReaderSettings.PRELOAD_STEPS.map { it to if (it == 0) "关" else "$it" },
                value = reader.preload,
                onChange = { v -> onChange { it.copy(preload = v) } },
            )
        }
        RowDivider()
        SettingRow(
            label = "翻页动画",
            // 四种，与 Web 的 pageAnim 一一对应；设置页这份是等分控件，两字选项一格一个字，
            // 含义用 hint 补
            hint = when (reader.animation) {
                PageAnimation.PAN -> "整页跟手平移"
                PageAnimation.SLIDE -> "新页轻微推入"
                PageAnimation.FADE -> "新旧页交叉淡入"
                PageAnimation.NONE -> null
            },
        ) {
            BrandSegmented(
                options = listOf(
                    PageAnimation.PAN to "平移",
                    PageAnimation.SLIDE to "轻移",
                    PageAnimation.FADE to "淡入",
                    PageAnimation.NONE to "无",
                ),
                value = reader.animation,
                onChange = { v -> onChange { it.copy(animation = v) } },
            )
        }
        RowDivider()
        ToggleRow(
            label = "双指缩放",
            hint = "阅读器内两指缩放，放大后可拖动查看",
            on = reader.pinchZoom,
            onChange = { v -> onChange { it.copy(pinchZoom = v) } },
        )
        RowDivider()
        ToggleRow(
            label = "保持常亮",
            on = reader.keepScreenOn,
            onChange = { v -> onChange { it.copy(keepScreenOn = v) } },
        )
    }
}

/**
 * 主题（Web 的「外观 / 高亮颜色」两行）。
 * 两项都按设备存，不随账号同步 —— 底部那句说明不能少，否则用户会以为换手机没同步是 bug。
 */
@Composable
private fun ThemeSection(
    appearance: Appearance,
    onTheme: (ThemeMode) -> Unit,
    onAccent: (Accent) -> Unit,
) {
    Section("主题") {
        SettingRow(label = "外观") {
            BrandSegmented(
                options = listOf(
                    ThemeMode.DARK to "深色",
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.SYSTEM to "跟随",
                ),
                value = appearance.theme,
                onChange = onTheme,
            )
        }
        RowDivider()
        // Web 这一行**不是** `block`：标签在左、四个色板贴着右缘排成一行
        InlineSettingRow(
            label = "高亮颜色",
            hint = "强调色 · 当前${accentLabel(appearance.accent)}",
        ) {
            Accent.entries.forEach { accent ->
                AccentSwatch(
                    accent = accent,
                    selected = appearance.accent == accent,
                    onClick = { onAccent(accent) },
                )
            }
        }
        RowDivider()
        Text(
            "主题与高亮色只影响本设备，不会同步到 NAS",
            fontSize = 11.sp,
            color = LocalInk.current.ink500,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** 存储与扫描（Web 的四行）：缓存用量 / 缓存上限 / 自动扫描间隔 / 立即全量扫描。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageSection(state: SettingsUiState, vm: SettingsViewModel) {
    Section("存储与扫描") {
        val cache = state.cache
        SettingRow(
            label = "缓存用量",
            hint = cache?.let { "${it.files} 个文件 · 上限 ${formatBytes(it.limitBytes)}" }
                ?: "统计中…",
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cache?.let { formatBytes(it.totalBytes) } ?: "—",
                    fontSize = 14.sp,
                    color = LocalInk.current.ink300,
                )
                Spacer(Modifier.weight(1f))
                GhostButton(
                    text = if (state.cacheBusy) "清理中…" else "清理",
                    onClick = vm::clearCache,
                    enabled = !state.cacheBusy && cache != null,
                    small = true, // Web：px-2 py-1 text-xs
                )
            }
        }
        RowDivider()
        SettingRow(label = "缓存上限", hint = "超出后自动淘汰最久未用的缩略图") {
            // Web 这里是 `Segmented fill stretch`（整条分段控件），不是一排独立胶囊
            BrandSegmented(
                options = CACHE_LIMIT_OPTIONS.map { mb ->
                    mb to if (mb >= 1024) "${mb / 1024}G" else "${mb}M"
                },
                // settings 还没回来时给一个不存在的值：无选中块，但控件已在位（不跳）
                value = state.settings?.thumbnailCacheMB ?: 0,
                enabled = !state.cacheBusy,
                onChange = { vm.setCacheLimitMB(it) },
            )
        }
        RowDivider()
        SettingRow(label = "自动扫描间隔", hint = "0 = 仅手动") {
            BrandSegmented(
                options = AUTO_SCAN_OPTIONS.map { hours ->
                    hours to if (hours == 0) "手动" else "${hours}h"
                },
                value = state.settings?.autoScanIntervalHours ?: -1,
                onChange = { vm.setAutoScanInterval(it) },
            )
        }
        RowDivider()
        SettingRow(label = "立即全量扫描", hint = "增量比对，只解析新增/变更") {
            val scan = state.scan
            Column {
                BrandButton(
                    text = if (state.scanBusy) "扫描中…" else "开始扫描",
                    onClick = vm::triggerScan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.scanBusy,
                )
                if (scan?.running == true) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (scan.total > 0) "正在进行：${scan.done} / ${scan.total}" else "正在扫描…",
                        fontSize = 11.sp,
                        color = LocalInk.current.ink500,
                    )
                }
            }
        }
    }
}

/** 数据备份（Web 的两行）：导出 / 导入，外加一行结果说明。 */
@Composable
private fun BackupSection(
    state: SettingsUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Section("数据备份") {
        SettingRow(
            label = "导出阅读数据",
            hint = "进度、分章已读、收藏、设置 → JSON 文件",
        ) {
            GhostButton(
                text = if (state.backupBusy == "export") "导出中…" else "导出",
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.backupBusy.isEmpty(),
                small = true, // Web：px-3 py-1.5 text-xs
            )
        }
        RowDivider()
        SettingRow(
            label = "导入阅读数据",
            hint = "合并导入，不会删除本地数据；仅匹配本机已有的漫画",
        ) {
            GhostButton(
                text = if (state.backupBusy == "import") "导入中…" else "选择文件",
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.backupBusy.isEmpty(),
                small = true, // Web：px-3 py-1.5 text-xs
            )
        }
        if (state.backupMsg.isNotEmpty()) {
            RowDivider()
            Text(
                state.backupMsg,
                fontSize = 11.sp,
                color = LocalInk.current.ink400,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * 登录保护（单账号门禁）。默认关闭 —— 升级不打断现有用户；开启后所有页面需登录。
 *
 * 一个不能省的顺序：**没设过密码不能直接开**，否则等于把自己锁在门外。
 * 这时先把表单打开、填完密码再连带开启（`enableAfter`）。
 */
@Composable
private fun AuthSection(state: SettingsUiState, vm: SettingsViewModel) {
    Section("登录保护") {
        var formOpen by rememberSaveable { mutableStateOf(false) }
        var enableAfter by rememberSaveable { mutableStateOf(false) }
        var user by rememberSaveable { mutableStateOf("") }
        var pass1 by rememberSaveable { mutableStateOf("") }
        var pass2 by rememberSaveable { mutableStateOf("") }
        var localError by rememberSaveable { mutableStateOf("") }

        val enabled = state.auth?.enabled == true
        ToggleRow(
            label = "启用登录保护",
            // Web：`已开启${isAuthed && auth.username ? ` · 当前用户 ${auth.username}` : ''}`
            hint = if (enabled) {
                val u = state.auth?.username
                if (!u.isNullOrBlank()) "已开启 · 当前用户 $u" else "已开启"
            } else {
                "关闭时局域网内任何人可直接访问"
            },
            on = enabled,
            enabled = !state.authBusy && state.auth != null,
            onChange = { next ->
                localError = ""
                if (!next) {
                    vm.setAuthEnabled(false)
                } else if (state.auth?.hasCredential != true) {
                    // 没设过密码不能直接开：否则把自己锁在外面（服务端也会拒）
                    formOpen = true
                    enableAfter = true
                    user = user.ifBlank { "admin" }
                } else {
                    vm.setAuthEnabled(true)
                }
            },
        )

        RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.auth?.hasCredential == true) "修改用户名 / 密码" else "设置用户名 / 密码",
                    fontSize = 14.sp,
                    color = LocalInk.current.ink100,
                )
                Text(
                    "至少 6 位；修改后会踢掉其它设备的登录",
                    fontSize = 11.sp,
                    color = LocalInk.current.ink500,
                )
            }
            Spacer(Modifier.width(16.dp))
            GhostButton(
                text = if (formOpen) "收起" else if (state.auth?.hasCredential == true) "修改" else "设置",
                onClick = {
                    formOpen = !formOpen
                    localError = ""
                    enableAfter = false
                    user = state.auth?.username ?: "admin"
                    pass1 = ""
                    pass2 = ""
                },
                small = true, // Web：px-3 py-1.5 text-xs
            )
        }

        if (formOpen) {
            LaunchedEffect(Unit) {
                if (user.isBlank()) user = state.auth?.username ?: "admin"
            }
            // Web：`space-y-2 px-4 py-3`
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                FormInput(
                    value = user,
                    onValueChange = { user = it },
                    placeholder = "用户名",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FormInput(
                    value = pass1,
                    onValueChange = { pass1 = it },
                    placeholder = "密码（至少 6 位）",
                    password = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FormInput(
                    value = pass2,
                    onValueChange = { pass2 = it },
                    placeholder = "再输一次密码",
                    password = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (localError.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(localError, fontSize = 11.sp, color = ToastErrorRed)
                }
                Spacer(Modifier.height(10.dp))
                BrandButton(
                    text = if (state.credentialsBusy) "保存中…" else "保存",
                    onClick = {
                        when {
                            user.isBlank() -> localError = "请输入用户名"
                            pass1.length < 6 -> localError = "密码至少 6 位"
                            pass1 != pass2 -> localError = "两次输入的密码不一致"
                            else -> {
                                localError = ""
                                val pwd = pass1
                                val after = enableAfter
                                formOpen = false
                                enableAfter = false
                                pass1 = ""
                                pass2 = ""
                                vm.setCredentials(user, pwd, after)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.credentialsBusy,
                )
            }
        }

        if (enabled) {
            RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("退出登录", fontSize = 14.sp, color = LocalInk.current.ink100)
                    Text(
                        "仅影响本机；其它设备的会话仍然有效",
                        fontSize = 11.sp,
                        color = LocalInk.current.ink500,
                    )
                }
                Spacer(Modifier.width(16.dp))
                GhostButton(
                    text = if (state.loggingOut) "退出中…" else "退出",
                    onClick = vm::logout,
                    enabled = !state.loggingOut,
                    small = true, // Web：px-3 py-1.5 text-xs
                )
            }
        }
    }
}

/**
 * 关于（逐行对齐 Web `pages/Settings.tsx:555-621`）。
 *
 * 结构是「卡片里的头部 + 六行 Row + 版本告警 + 卡片外的说明 + 收尾签名」，
 * 一行都不能少 —— 这几行是排查「装了旧包 / 连错了机器 / 数据目录没权限」的唯一入口。
 * 客户端特有的「服务端地址」插在最前面：那是「我这端连的是哪台机器」的答案，
 * 与「服务端跑的是什么」天然是同一类问题。
 */
@Composable
private fun AboutSection(state: SettingsUiState) {
    val ink = LocalInk.current
    val about = state.about
    Section("关于") {
        // 头部：Web 是 BrandLogo 44 + 名称 + 版本徽章 + 一行副标题
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp), // Web `px-4 py-4`
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    // Web `rounded-[22%]`：圆角按边长的 22% 取
                    .clip(RoundedCornerShape(22)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        about?.name ?: state.health?.name ?: "肥牛漫画",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink.ink100,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    // Web：`rounded bg-ink-800 px-1.5 py-0.5 text-[11px] text-brand-400 align-middle`
                    Text(
                        "v${about?.version ?: "—"}",
                        fontSize = 11.sp,
                        color = accentTextColor(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ink.ink800)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "飞牛 NAS 漫画阅读器 · Android 客户端",
                    fontSize = 11.sp,
                    color = ink.ink500,
                )
            }
        }
        RowDivider()
        InfoRow("服务端地址", hint = state.serverUrl ?: "—")
        RowDivider()
        // Web 把「已运行」放在这一行的**右侧**：左侧是运行环境（hint），右侧是状态
        InfoRow(
            "运行环境",
            hint = about?.let { "Go ${it.node} · ${it.platform}" } ?: "读取中…",
            trailing = about?.let { "已运行 ${formatUptime(it.uptimeSec)}" },
        )
        RowDivider()
        InfoRow(
            "构建时间",
            hint = about?.let { formatTimestamp(it.builtAt) } ?: "未知",
            trailing = about?.let { "端口 ${it.port}" },
        )
        RowDivider()
        // Web 叫「前端版本」，左侧 label + hint（服务端版本）、右侧本端版本。
        // 版本码是 Android 特有的排障信息，挂在右侧版本号后面（不影响三段版式）。
        InfoRow(
            "前端版本",
            hint = about?.let { "服务端 v${it.version}" } ?: "读取中…",
            trailing = "v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
        )
        RowDivider()
        // Web 这一行的右侧是**常驻**的可写状态，不是只在不可写时才提示：
        // 读到「数据目录可写」本身就是「进度存得进去」的确认
        InfoRow(
            "漫画根目录",
            hint = about?.comicRoot?.ifBlank { "—" } ?: "—",
            trailing = about?.let { if (it.dataWritable) "数据目录可写" else "数据目录不可写" },
            trailingColor = if (about?.dataWritable == false) ToastErrorRed else null,
        )
        RowDivider()
        InfoRow(
            "数据目录",
            hint = about?.dataDir?.ifBlank { "—" } ?: "—",
            trailing = about?.let { formatBytes(it.dbBytes) },
        )
        RowDivider()
        InfoRow("数据库文件", hint = about?.dbFile?.ifBlank { "—" } ?: "—")
        if (about?.dataWritable == false) {
            RowDivider()
            Text(
                "数据目录不可写：阅读进度与收藏都无法保存，请检查 NAS 上的目录权限",
                fontSize = 11.sp,
                color = ToastErrorRed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        state.warning?.let { warn ->
            RowDivider()
            Text(
                warn,
                fontSize = 11.sp,
                color = AmberWarn,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
    // 卡片外：Web 的同款说明（只读扫描 + 数据落在哪个目录）
    Text(
        "漫画目录只读扫描，不会修改或删除你的文件；阅读进度、收藏、分类与设置都保存在上面的数据目录里（SQLite），" +
            "换机时可用「数据备份」导出导入。",
        fontSize = 11.sp,
        lineHeight = 18.sp,
        color = ink.ink500,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
    )
    // 收尾签名：抱书娘站在这里代替一句「感谢使用」（Web `Mascot kind="loading"` + 「卷卷·Karole」）
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(MascotKind.LOADING, size = 112.dp)
        Text(
            "卷卷·Karole",
            fontSize = 11.sp,
            letterSpacing = 0.3.sp, // Web tracking-wide ≈ 0.025em
            color = ink.ink500,
        )
    }
}

@Composable
private fun VersionFooter() {
    // Web：`pb-4 text-center text-[11px] text-ink-600`
    Text(
        "肥牛漫画 v${BuildConfig.VERSION_NAME} · Android 客户端",
        fontSize = 11.sp,
        color = LocalInk.current.ink600,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    )
}

// ---- 小工具 ----

private fun accentLabel(accent: Accent): String = when (accent) {
    Accent.AMBER -> "琥珀橙"
    Accent.SORA -> "空色"
    Accent.BEAN -> "豆沙绿"
}

private fun todayStamp(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun formatTimestamp(millis: Long): String =
    if (millis <= 0) {
        "未知"
    } else {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }
