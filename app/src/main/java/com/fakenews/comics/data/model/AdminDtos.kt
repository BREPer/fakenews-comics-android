package com.fakenews.comics.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * GET /api/about —— 运行环境信息（设置页「关于」区）。
 * 字段名与服务端 `routes/health.go` 一一对应。
 */
@Serializable
data class AboutDto(
    val name: String = "",
    val version: String = "",
    val builtAt: Long = 0,
    /** 后端是 Go，`node` 里装的是 `runtime.Version()`（go1.x.y），按 Go 呈现 */
    val node: String = "",
    val platform: String = "",
    /** 服务端回的是 JSON 数字（`e.Port`），别写成 String——kotlinx 会整条解析失败 */
    val port: Int = 0,
    val uptimeSec: Long = 0,
    val comicRoot: String = "",
    val dataDir: String = "",
    val dataWritable: Boolean = true,
    val dbFile: String = "",
    val dbBytes: Long = 0,
)

/** GET /api/cache/stats —— 缩略图/封面缓存用量 */
@Serializable
data class CacheStatsDto(
    val totalBytes: Long = 0,
    val files: Int = 0,
    val limitBytes: Long = 0,
    val thumbsBytes: Long = 0,
    val coversBytes: Long = 0,
    val customCoversBytes: Long = 0,
)

/** POST /api/cache/clear */
@Serializable
data class ClearCacheDto(
    val ok: Boolean = false,
    val freed: Long = 0,
)

/** POST /api/cache/evict */
@Serializable
data class EvictCacheDto(
    val ok: Boolean = false,
    val freed: Long = 0,
    val deleted: Int = 0,
    val stats: CacheStatsDto? = null,
)

/**
 * PUT /api/comics/{id} —— 编辑元数据。
 *
 * 全部可为 null：服务端用指针区分「没传」与「传了空字符串」，
 * kotlinx-serialization 默认不输出等于默认值的字段，所以只写改过的那几项。
 */
@Serializable
data class UpdateComicBody(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
)

/** POST /api/comics/{id}/scan —— 单部重扫结果 */
@Serializable
data class RescanResultDto(
    val chapterCount: Int = 0,
    val pageCount: Int = 0,
    val updated: Boolean = false,
)

/**
 * GET /api/scan/status —— 扫描进度（对齐 Web `types.ScanStatus`）。
 *
 * 扫描是这套系统里唯一的长时间操作（几分钟到几十分钟）。Web 首页常驻一个
 * `useScanWatch(true, 15_000)` 的观察器：进行中 800ms 轮询、空闲 15s 轮询，
 * 并在「running → 结束」那一刻自动刷新列表 —— 不刷的话首扫结束后首页
 * 还停在「还没有漫画」的旧结果上，用户会以为导入失败。
 */
@Serializable
@Immutable
data class ScanStatusDto(
    val running: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val skipped: Int = 0,
    val fullParse: Boolean = false,
    /** 跳过扫描的库（离线/不可达），Web 在漫画库页展示 */
    val skippedLibs: List<String> = emptyList(),
    /** 当前正在扫的路径，供「正在进行 xxx」文案 */
    val current: String = "",
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
) {
    /** 0..100，total 为 0 时给 0（Web 同算法） */
    val percent: Int
        get() = if (total <= 0) 0 else minOf(100, (done * 100 + total / 2) / total)
}

/** PUT /api/auth/credentials */
@Serializable
data class CredentialsBody(
    val username: String,
    val password: String,
)

/** PUT /api/auth/enabled */
@Serializable
data class AuthEnabledBody(
    val enabled: Boolean,
)

@Serializable
data class AuthEnabledDto(
    val enabled: Boolean = false,
)

/**
 * GET/PUT /api/settings —— 只剩服务端执行的两项。
 *
 * 阅读偏好与外观按设备存（见 `data/session/ReaderPrefs.kt`），
 * 服务端从 2.0.2 起不再保存这些键，改 PUT 会被静默忽略。
 */
@Serializable
data class SettingsDto(
    val autoScanIntervalHours: Int = 0,
    val thumbnailCacheMB: Int = 0,
)

/**
 * GET /api/libraries —— 漫画库列表（对齐 Web `types.Library`）。
 *
 * 字段名与服务端 `routes/libraries.go` 一一对应。`comicCount` 由服务端聚合，
 * `inaccessiblePaths` 是「卷没挂载 / fnOS 没授权文件夹访问」的目录 ——
 * 有值时漫画库卡片会显示一条黄色告警，否则用户只会看到「书架是空的」却不知道为什么。
 */
@Serializable
@Immutable
data class LibraryDto(
    val id: String = "",
    val name: String = "",
    /** `chapter`（章节型：每个子目录一部）或 `single`（单篇型：每个含图目录各自成书） */
    val type: String = "chapter",
    val paths: List<String> = emptyList(),
    val sort: Int = 0,
    val createdAt: Long = 0,
    val comicCount: Int = 0,
    val inaccessiblePaths: List<String> = emptyList(),
)

/**
 * POST /api/libraries 的请求体。
 *
 * `type` 只有 `chapter` / `single` 两种，服务端把非 `single` 一律归一到 `chapter`
 * （TS 版同口径），所以这里也只出这两个字符串。
 */
@Serializable
data class LibraryBody(
    val name: String,
    val type: String = "chapter",
    val paths: List<String>,
)

/** POST /api/libraries 的返回：只有新库 id。 */
@Serializable
data class LibraryCreateDto(
    val id: String = "",
)

/**
 * PATCH /api/libraries/{id} 的请求体。
 *
 * 与 [UpdateComicBody] 同一套口径：全部可为 null，服务端用指针区分「没传」与
 * 「传了空字符串」，而 kotlinx-serialization 默认不输出等于默认值的字段 ——
 * 于是编辑库时只写改过的那一项（比如只想改目录就别把名称也发过去）。
 */
@Serializable
data class LibraryPatchBody(
    val name: String? = null,
    val type: String? = null,
    val paths: List<String>? = null,
)

/** DELETE /api/libraries/{id} —— `removedComics` 是被级联清掉的漫画条目数。 */
@Serializable
data class LibraryDeleteDto(
    val ok: Boolean = false,
    val removedComics: Int = 0,
)

/**
 * GET /api/libraries/browse?path=… —— 目录点选器的一层（对齐 `routes/libraries.go`）。
 *
 * `leafHint` 是「这个目录直接含图或顶层压缩包」= 扫描器语义下**本身就是一部漫画**：
 * 选它只会收录这一部，选它的上层才能整片收录。UI 把它画成行尾的小标。
 */
@Serializable
data class LibraryBrowseDto(
    val path: String = "",
    val root: String = "",
    val leafHint: Boolean = false,
    val items: List<LibraryBrowseItemDto> = emptyList(),
)

@Serializable
data class LibraryBrowseItemDto(
    val name: String = "",
    val path: String = "",
    val leafHint: Boolean = false,
)

/**
 * POST /api/libraries/{id}/upload —— 上传结果（对齐 `routes/upload.go`）。
 *
 * `bytes` 服务端回的是数字；`folders` 只有章节型有值（解压出来的漫画文件夹名）。
 */
@Serializable
@Immutable
data class UploadResultDto(
    val written: Int = 0,
    val bytes: Long = 0,
    val skipped: List<String> = emptyList(),
    val libraryId: String = "",
    val scanning: Boolean = false,
    val folders: List<String> = emptyList(),
)

/** POST /api/scan 的请求体。全库扫描时传空对象（与 Web 的 `JSON.stringify({})` 同语义）。 */
@Serializable
data class ScanBody(
    val libraryId: String? = null,
)

/** POST /api/scan —— 触发扫描。`started=false` 表示已有扫描在进行中，不是失败。 */
@Serializable
data class ScanStartDto(
    val started: Boolean = false,
    val status: ScanStatusDto? = null,
)

/**
 * GET/POST /api/backup/(export|import) —— 阅读数据备份。
 *
 * `settings` 与 `devicePrefs` 都用 [JsonObject]：服务端只认识 `settings` 里的两三个键，
 * 其余原样透传；`devicePrefs` 是本机概念（Web 侧导出时由前端拼进文件、导入时再剥掉），
 * 用弱类型兜住，避免服务端将来加键时整条解析失败。
 */
@Serializable
data class BackupDto(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val progress: List<BackupProgressDto> = emptyList(),
    val favorites: List<BackupFavoriteDto> = emptyList(),
    val settings: JsonObject = JsonObject(emptyMap()),
    val devicePrefs: JsonObject? = null,
)

@Serializable
data class BackupProgressDto(
    val comicId: String,
    val chapterId: String,
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val percent: Double = 0.0,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupFavoriteDto(
    val comicId: String,
    val createdAt: Long = 0,
)

/** POST /api/backup/import 的返回：各项实际写入条数 + 跳过的条数（不属于本机的记录）。 */
@Serializable
data class ImportResultDto(
    val imported: ImportCountsDto = ImportCountsDto(),
    val skipped: Int = 0,
)

@Serializable
data class ImportCountsDto(
    val progress: Int = 0,
    val favorites: Int = 0,
    val settings: Int = 0,
)
