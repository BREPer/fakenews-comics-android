package com.fakenews.comics.data.net

import com.fakenews.comics.data.session.PageQuality
import com.fakenews.comics.data.session.ServerStore
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片地址（封面/页图）直连 ServerStore 的 baseUrl。
 *
 * 为什么不走 Retrofit 的占位地址 + 动态拦截器：Coil 用的是另一个不带
 * [DynamicBaseUrlInterceptor] 的 OkHttpClient（它已经带上了子路径），
 * 再拼一次会把 `/comics/` 这样的反代前缀叠成两份。
 */
@Singleton
class ImageUrls @Inject constructor(private val serverStore: ServerStore) {

    /**
     * 封面地址缓存。
     *
     * 网格里 `coverUrl(id)` 是**每次重组都会调用**的（每个 itemContent 一次），
     * 而 [build] 每次都要走一遍 `HttpUrl.Builder` 的路径段编码 + 字符串拼接。
     * 一屏 12 张卡、滚动时每帧都在重组，几百次 URL 构建纯属浪费 —— 这里的 key
     * 只由 `baseUrl + width + comicId` 决定，天然可缓存。
     *
     * LRU 512 条（一本书一条，够覆盖任何真实列表）；访问序 `accessOrder = true`。
     * 键里带 `baseUrl`：用户换服务器后旧地址自然失效，不会把上一个 NAS 的 URL 发给 Coil。
     *
     * 只在主线程调用（Compose 组合期），但仍然加锁 —— 万一以后有后台预取进来。
     */
    private val coverCache = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > 512
    }

    fun cover(comicId: String, width: Int = 320): String? {
        val base = serverStore.baseUrl ?: return null
        val key = "$base|$width|$comicId"
        synchronized(coverCache) {
            coverCache[key]?.let { return it }
        }
        val built = build("api/comics/${comicId}/cover") {
            addQueryParameter("w", width.toString())
        } ?: return null
        synchronized(coverCache) { coverCache[key] = built }
        return built
    }

    /**
     * 页图：`chapterId` 形如 `abc#0`，`#` 必须留在路径段里被转义（[build] 走
     * addPathSegments，会编成 %23）；自己拼字符串的话 `#` 会被当成 fragment 截掉。
     *
     * quality=standard 走服务端缩图（webp/jpeg），手机上看原图没有收益：
     * 一章 CBZ 的原图常有 3–8 MB，翻页会明显卡。「流畅」只是在客户端把目标宽度降到 720，
     * 不新增服务端契约（与 Web 版 pageUrl 同一套映射）。
     */
    fun page(
        comicId: String,
        chapterId: String,
        index: Int,
        quality: PageQuality = PageQuality.STANDARD,
    ): String? = build("api/comics/$comicId/chapters/$chapterId/pages/$index") {
        if (quality == PageQuality.ORIGINAL) {
            addQueryParameter("quality", "original")
        } else {
            addQueryParameter("quality", "standard")
            addQueryParameter(
                "w",
                (if (quality == PageQuality.SMOOTH) SMOOTH_WIDTH else STANDARD_WIDTH).toString(),
            )
        }
    }

    private inline fun build(path: String, queries: HttpUrl.Builder.() -> Unit): String? {
        val base = serverStore.baseUrl ?: return null
        return base.newBuilder().addPathSegments(path).apply(queries).build().toString()
    }

    companion object {
        /** 与 Web 版 standard 画质一致；再宽手机上肉眼无感，却明显更费流量。 */
        const val STANDARD_WIDTH = 1080

        /** 「流畅」档：对齐 Web 版的 720。 */
        const val SMOOTH_WIDTH = 720
    }
}
