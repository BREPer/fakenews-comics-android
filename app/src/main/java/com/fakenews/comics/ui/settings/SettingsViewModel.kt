package com.fakenews.comics.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.model.AboutDto
import com.fakenews.comics.data.model.AuthEnabledBody
import com.fakenews.comics.data.model.AuthStateDto
import com.fakenews.comics.data.model.BackupDto
import com.fakenews.comics.data.model.CacheStatsDto
import com.fakenews.comics.data.model.CredentialsBody
import com.fakenews.comics.data.model.HealthDto
import com.fakenews.comics.data.model.ScanBody
import com.fakenews.comics.data.model.ScanStatusDto
import com.fakenews.comics.data.model.SettingsDto
import com.fakenews.comics.data.model.versionWarning
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.CookieStore
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.session.Accent
import com.fakenews.comics.data.session.Appearance
import com.fakenews.comics.data.session.AppearancePrefs
import com.fakenews.comics.data.session.FitMode
import com.fakenews.comics.data.session.PageAnimation
import com.fakenews.comics.data.session.PageQuality
import com.fakenews.comics.data.session.ReadDir
import com.fakenews.comics.data.session.ReaderMode
import com.fakenews.comics.data.session.ReaderPrefs
import com.fakenews.comics.data.session.ReaderSettings
import com.fakenews.comics.data.session.ServerStore
import com.fakenews.comics.data.session.ThemeMode
import com.fakenews.comics.ui.common.UiMessage
import com.fakenews.comics.ui.common.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject

/** 缓存上限档位，与 Web 设置页的四个选项一致 */
val CACHE_LIMIT_OPTIONS = listOf(512, 1024, 2048, 4096)

/** 自动扫描间隔档位（Web：手动 / 6h / 24h），0 = 仅手动 */
val AUTO_SCAN_OPTIONS = listOf(0, 6, 24)

data class SettingsUiState(
    val serverUrl: String? = null,
    val health: HealthDto? = null,
    val auth: AuthStateDto? = null,
    val about: AboutDto? = null,
    val cache: CacheStatsDto? = null,
    val settings: SettingsDto? = null,
    val warning: String? = null,
    val loading: Boolean = true,
    /**
     * 是否**成功**加载过至少一次。
     *
     * 用来区分「首屏」和「刷新」：Web 的 `isLoading` 只在首屏为 true
     * （react-query 的 isLoading = isPending && isFetching），刷新时是 isFetching。
     * 首屏没数据时整页给 Loading / Error；刷新时保留已渲染的内容，只有按钮变「读取中…」。
     */
    val loaded: Boolean = false,
    val error: String? = null,
    val loggingOut: Boolean = false,
    val cacheBusy: Boolean = false,
    val authBusy: Boolean = false,
    val credentialsBusy: Boolean = false,
    val scanBusy: Boolean = false,
    /** 备份忙闲：`""` / `export` / `import` */
    val backupBusy: String = "",
    val backupMsg: String = "",
    val scan: ScanStatusDto? = null,
)

/**
 * 设置页状态机（Web `pages/Settings.tsx` 的六个区块）。
 *
 * 两类设置归属不同，这条线不能混（见 `docs/2026-10-24-偏好归属论证.md`）：
 *   - **阅读偏好 + 外观（10 项）**：按设备存，[ReaderPrefs] / [AppearancePrefs] 是唯一真源，
 *     同一账号在手机/平板上各用各的，改一个不影响别的设备；
 *   - **系统设置（自动扫描间隔 / 缓存上限）**：服务端执行，PUT 到 `/api/settings`。
 *
 * 备份导出时客户端要**自己**把 devicePrefs 拼进文件：它不在服务端，而备份是跨设备迁移的
 * 唯一通道 —— 不拼的话换机恢复会丢主题与阅读偏好（Web 同一处逻辑）。
 * 字段名与 Web 逐字一致（`readerMode` / `preloadCount` / `theme` …），
 * 所以 Android 导出的文件可以直接导进 Web，反之亦然。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: FakenewsApi,
    private val runner: ApiRunner,
    private val serverStore: ServerStore,
    private val cookieStore: CookieStore,
    private val appearancePrefs: AppearancePrefs,
    private val readerPrefs: ReaderPrefs,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(serverUrl = serverStore.savedUrl))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** 外观偏好（主题 / 高亮色）：按设备存，MainActivity 也订阅同一份真源 */
    val appearance: StateFlow<Appearance> = appearancePrefs.state

    /**
     * 阅读偏好（8 项）：直接观察 [ReaderPrefs.state] 这个唯一真源。
     *
     * 这里原来做了一层镜像（`MutableStateFlow(readerPrefs.settings)`），只在构造时取一次值 ——
     * 于是「阅读器里把翻页模式改成双页 → 回设置页仍高亮在单页」（实测 reader.xml 已是 DOUBLE）。
     * 阅读器和设置页现在共用同一个 flow，任何一侧写入两边都会立刻看到。
     */
    val reader: StateFlow<ReaderSettings> = readerPrefs.state

    fun setTheme(theme: ThemeMode) = appearancePrefs.setTheme(theme)

    fun setAccent(accent: Accent) = appearancePrefs.setAccent(accent)

    /** 改任意一项阅读偏好：先写 SharedPreferences，再更新镜像（阅读器下次进入即生效）。 */
    fun setReader(change: (ReaderSettings) -> ReaderSettings) {
        val current = readerPrefs.state.value
        val next = change(current)
        if (next == current) return
        // setter 内部会落盘并更新同一个 flow，镜像不用再写一次
        readerPrefs.settings = next
    }

    private val _loggedOut = Channel<Unit>(Channel.BUFFERED)
    val loggedOut = _loggedOut.receiveAsFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        // 备份文件必须带全字段：Web 端按 `data.progress.length` 统计导出条数，
        // 空数组被省略会直接报错。
        encodeDefaults = true
    }

    init {
        refresh()
        refreshScan()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, serverUrl = serverStore.savedUrl) }
            try {
                // health 是「这些副信息该不该显示」的前提，先确认它；后面四项互不依赖，
                // 必须并发 —— 串行时设置页首屏 = 5 × RTT，NAS 上一跳 100ms+，等于白等半秒。
                val health = runner.call { api.health() }
                coroutineScope {
                    val authJob = async { runCatchingCancellable { api.authStatus() } }
                    val aboutJob = async { runCatchingCancellable { api.about() } }
                    val cacheJob = async { runCatchingCancellable { api.cacheStats() } }
                    val settingsJob = async { runCatchingCancellable { api.getSettings() } }
                    val auth = authJob.await()
                    val about = aboutJob.await()
                    val cache = cacheJob.await()
                    val settings = settingsJob.await()
                    _state.update {
                        it.copy(
                            loading = false,
                            loaded = true,
                            health = health,
                            auth = auth,
                            about = about,
                            cache = cache,
                            settings = settings,
                            warning = versionWarning(health.version),
                            error = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "读取服务端信息失败") }
            }
        }
    }

    fun refreshScan() {
        viewModelScope.launch {
            try {
                val st = runner.call { api.scanStatus() }
                _state.update { it.copy(scan = st) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 扫描状态是辅助信息：拿不到就不显示进度
            }
        }
    }

    /** 清理缓存：反馈里必须说出释放了多少，否则点了只看到按钮文字变一下。 */
    fun clearCache() {
        if (_state.value.cacheBusy) return
        _state.update { it.copy(cacheBusy = true) }
        viewModelScope.launch {
            try {
                val res = runner.call { api.clearCache() }
                val stats = runCatchingCancellable { api.cacheStats() }
                _state.update { it.copy(cacheBusy = false, cache = stats ?: it.cache) }
                _messages.send(UiMessage.ok("已清理，释放 ${formatBytes(res.freed)}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(cacheBusy = false) }
                _messages.send(UiMessage.err("清理失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    /**
     * 改缓存上限。
     *
     * 顺序不能反：Web 端早期版本把「存上限」与「淘汰」并发发出，淘汰接口读到的还是旧上限
     * → 一个文件都不删，界面却以为已经缩容。必须先 PUT /api/settings 再 /cache/evict。
     *
     * **另一项为什么可以传 0**：[SettingsDto] 的两个字段默认值都是 0，而全局 Json
     * （`di/AppModule.kt`）没有开 `encodeDefaults`，所以值为 0 的字段**不会出现在请求体里**，
     * 服务端 `routes/settings.go` 又是「键存在才更新」的 partial update —— 于是这里即便
     * 传 `autoScanIntervalHours = 0` 也不会把用户的定时扫描关掉。
     * 这个安全性是三个条件同时成立换来的（默认值 0 + 不序列化默认值 + 服务端按 key 判断），
     * 任何一个变了都会静默清掉对方设置 —— 改这三处任意一处时先回来看这段。
     */
    fun setCacheLimitMB(mb: Int) {
        if (_state.value.cacheBusy) return
        _state.update { it.copy(cacheBusy = true) }
        viewModelScope.launch {
            try {
                runner.call { api.putSettings(SettingsDto(thumbnailCacheMB = mb)) }
                val res = runCatchingCancellable { api.evictCache() }
                _state.update {
                    it.copy(
                        cacheBusy = false,
                        cache = res?.stats ?: it.cache,
                        settings = SettingsDto(
                            thumbnailCacheMB = mb,
                            autoScanIntervalHours = it.settings?.autoScanIntervalHours ?: 0,
                        ),
                    )
                }
                val deleted = res?.deleted ?: 0
                if (deleted > 0) {
                    _messages.send(UiMessage.ok("已释放 ${formatBytes(res!!.freed)}（$deleted 个文件）"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(cacheBusy = false) }
                _messages.send(UiMessage.err("设置未保存：${e.message ?: "未知错误"}"))
            }
        }
    }

    /** 改自动扫描间隔（0 = 仅手动）。 */
    fun setAutoScanInterval(hours: Int) {
        val current = _state.value.settings?.autoScanIntervalHours
        if (current == hours) return
        // 乐观更新：分段选择器要立刻停在新的档位上
        _state.update {
            it.copy(
                settings = it.settings?.copy(autoScanIntervalHours = hours)
                    ?: SettingsDto(autoScanIntervalHours = hours),
            )
        }
        viewModelScope.launch {
            try {
                runner.call {
                    api.putSettings(
                        SettingsDto(
                            autoScanIntervalHours = hours,
                            thumbnailCacheMB = _state.value.settings?.thumbnailCacheMB ?: 0,
                        ),
                    )
                }
                _messages.send(UiMessage.ok(if (hours == 0) "已改为仅手动扫描" else "自动扫描间隔已改为 $hours 小时"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(UiMessage.err("设置未保存：${e.message ?: "未知错误"}"))
                refresh()
            }
        }
    }

    /** 「立即全量扫描」：增量比对，只解析新增/变更。 */
    fun triggerScan() {
        if (_state.value.scanBusy) return
        _state.update { it.copy(scanBusy = true) }
        viewModelScope.launch {
            try {
                val res = runner.call { api.triggerScan(ScanBody()) }
                _state.update { it.copy(scanBusy = false, scan = res.status ?: it.scan) }
                _messages.send(if (res.started) UiMessage.ok("已开始扫描") else UiMessage.info("已有扫描在进行中，请稍候"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(scanBusy = false) }
                _messages.send(UiMessage.err("扫描启动失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    // ---- 数据备份 ----

    /**
     * 导出到用户选定的文件（[uri] 来自 `CreateDocument`）。
     *
     * 服务端只给「进度 / 收藏 / 设置」，本机偏好由客户端补进 `devicePrefs` ——
     * 备份是跨设备迁移的唯一通道，缺了它换机后主题与阅读偏好全部回默认。
     */
    fun exportBackup(uri: Uri) {
        if (_state.value.backupBusy.isNotEmpty()) return
        _state.update { it.copy(backupBusy = "export", backupMsg = "") }
        viewModelScope.launch {
            try {
                val data = runner.call { api.exportBackup() }
                val payload = data.copy(devicePrefs = devicePrefsJson())
                val text = json.encodeToString(BackupDto.serializer(), payload)
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("无法写入所选文件")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                _state.update {
                    it.copy(
                        backupBusy = "",
                        backupMsg = "已导出：进度 ${data.progress.size} 条、" +
                            "收藏 ${data.favorites.size} 条、本机偏好 10 项",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(backupBusy = "", backupMsg = "导出失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    /**
     * 从用户选定的文件导入（[uri] 来自 `OpenDocument`）。
     *
     * `devicePrefs` 先应用、再从请求体里剥掉 —— 服务端不认识这个键，
     * 原样发过去会被当成一条普通 settings 写进库（Web 同样先删掉再提交）。
     */
    fun importBackup(uri: Uri) {
        if (_state.value.backupBusy.isNotEmpty()) return
        _state.update { it.copy(backupBusy = "import", backupMsg = "") }
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("无法读取所选文件")
                    stream.use { it.bufferedReader(Charsets.UTF_8).readText() }
                }
                val root = json.parseToJsonElement(text).jsonObject
                val prefs = root["devicePrefs"] as? JsonObject
                val body = json.decodeFromJsonElement(
                    BackupDto.serializer(),
                    JsonObject(root - "devicePrefs"),
                )
                val res = runner.call { api.importBackup(body) }
                prefs?.let { applyDevicePrefs(it) }
                val msg = buildString {
                    append("导入完成：进度 ${res.imported.progress}、")
                    append("收藏 ${res.imported.favorites}、")
                    append("设置 ${res.imported.settings}")
                    if (prefs != null) append("、本机偏好已应用")
                    if (res.skipped > 0) append("（跳过 ${res.skipped} 条不属于本机的记录）")
                }
                _state.update { it.copy(backupBusy = "", backupMsg = msg) }
                // 导入会改进度与收藏，页面上的缓存/关于信息也顺手刷新
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(backupBusy = "", backupMsg = "导入失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    /** 十项本机偏好 → 与 Web `DEVICE_PREFS_DEFAULTS` 同名的 JSON（值也是同一套小写串）。 */
    private fun devicePrefsJson(): JsonObject {
        val r = readerPrefs.state.value
        val a = appearancePrefs.state.value
        return buildJsonObject {
            put("readerMode", r.mode.name.lowercase())
            put("readDir", r.readDir.name.lowercase())
            put("fitMode", r.fit.name.lowercase())
            put("quality", r.quality.name.lowercase())
            put("preloadCount", r.preload)
            put("pageAnimation", r.animation.name.lowercase())
            put("keepScreenOn", r.keepScreenOn)
            put("pinchZoom", r.pinchZoom)
            put("theme", a.theme.name.lowercase())
            put("accent", a.accent.key)
        }
    }

    /** 备份文件里的 devicePrefs → 本机偏好。字段缺失就保持当前值（不重置）。 */
    private fun applyDevicePrefs(obj: JsonObject) {
        fun str(key: String): String? = obj[key]?.jsonPrimitive?.contentOrNull
        fun bool(key: String): Boolean? = obj[key]?.jsonPrimitive?.booleanOrNull
        fun int(key: String): Int? = obj[key]?.jsonPrimitive?.intOrNull

        val current = readerPrefs.state.value
        val next = current.copy(
            mode = str("readerMode")?.let { ReaderMode.from(it.uppercase()) } ?: current.mode,
            readDir = str("readDir")?.let { ReadDir.from(it.uppercase()) } ?: current.readDir,
            fit = str("fitMode")?.let { FitMode.from(it.uppercase()) } ?: current.fit,
            quality = str("quality")?.let { PageQuality.from(it.uppercase()) } ?: current.quality,
            preload = int("preloadCount")?.let { ReaderSettings.snapPreload(it) } ?: current.preload,
            animation = str("pageAnimation")
                ?.let { PageAnimation.from(it.uppercase()) } ?: current.animation,
            keepScreenOn = bool("keepScreenOn") ?: current.keepScreenOn,
            pinchZoom = bool("pinchZoom") ?: current.pinchZoom,
        )
        if (next != current) {
            readerPrefs.settings = next
        }
        str("theme")?.let { raw ->
            ThemeMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?.let(appearancePrefs::setTheme)
        }
        str("accent")?.let { raw -> appearancePrefs.setAccent(Accent.from(raw)) }
    }

    // ---- 登录保护 ----

    /** 开关登录保护。开启前必须已经设过密码，否则等于把自己锁在门外（服务端也会拒）。 */
    fun setAuthEnabled(enabled: Boolean) {
        if (_state.value.authBusy) return
        _state.update { it.copy(authBusy = true) }
        viewModelScope.launch {
            try {
                runner.call { api.setAuthEnabled(AuthEnabledBody(enabled)) }
                val auth = runCatchingCancellable { api.authStatus() }
                _state.update { it.copy(authBusy = false, auth = auth ?: it.auth) }
                _messages.send(UiMessage.ok(if (enabled) "已开启登录保护" else "已关闭登录保护"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(authBusy = false) }
                _messages.send(UiMessage.err("设置失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    /**
     * 设置/修改用户名密码。
     *
     * 改完服务端会踢掉全部会话，但同时给这一次请求补发新 cookie（否则用户改完自己被踢），
     * OkHttp 的 CookieJar 会自动存下，客户端不必另行处理。
     */
    fun setCredentials(username: String, password: String, enableAfter: Boolean = false) {
        if (_state.value.credentialsBusy) return
        _state.update { it.copy(credentialsBusy = true) }
        viewModelScope.launch {
            try {
                runner.call { api.setCredentials(CredentialsBody(username.trim(), password)) }
                if (enableAfter) {
                    runner.call { api.setAuthEnabled(AuthEnabledBody(true)) }
                }
                val auth = runCatchingCancellable { api.authStatus() }
                _state.update { it.copy(credentialsBusy = false, auth = auth ?: it.auth) }
                _messages.send(UiMessage.ok(if (enableAfter) "已保存并开启登录保护" else "用户名与密码已更新"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(credentialsBusy = false) }
                _messages.send(UiMessage.err("保存失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    fun logout() {
        if (_state.value.loggingOut) return
        _state.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            try {
                runner.call { api.logout() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 服务端不可达时仍丢弃本地会话：退出登录是用户的明确意图
            }
            cookieStore.clearAll()
            _loggedOut.send(Unit)
        }
    }

    /**
     * 次要信息（ auth / about / cache / settings）的读取失败一律降级为 null。
     * 取消异常必须往外抛，否则协程的取消语义会被吃掉。
     */
    private suspend fun <T> runCatchingCancellable(
        block: suspend () -> retrofit2.Response<com.fakenews.comics.data.net.ApiEnvelope<T>>,
    ): T? =
        try {
            runner.call(block)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
