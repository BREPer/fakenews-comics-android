package com.fakenews.comics.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预加载池：长期持有已解码位图 + LRU 淘汰。
 *
 * 对应 Web 端 `components/reader/page-image.tsx` 的 `preloadPool`。
 * 两件必须做的事：
 *   ① **长期持有引用**——只在 effect 里 `enqueue` 是不够的：内存强引用一旦被 Coil 的
 *      缓存策略淘汰，新页面挂载时仍要重新解码，那一帧画不出来就是「翻页短暂空白」。
 *   ② **上限 [KEEP]**——每张 1080 宽的页图约 1080×1600×4B ≈ 7MB，12 张 ≈ 84MB。
 *      手机上一路翻不看回收会明显推高内存（被系统杀进程的常见原因），
 *      而真正需要覆盖的只有「当前页 ± 预加载页数（默认 3、最大 8）」。
 *
 * [LinkHashMapKt] 用 accessOrder=true：命中就移到队尾，往回翻那一页不会恰好是最早被淘汰的。
 */
@Singleton
class PagePreloadPool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) {
    private val lock = Any()
    private val pool = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > KEEP
    }

    /** 当前持有的位图数（测试观察用） */
    val size: Int get() = synchronized(lock) { pool.size }

    /**
     * 预热一张：拿不到位图（404/解码失败）也不抛，正常加载路径自己会重试。
     * 已在池中则只做一次命中标记（reorder）。
     */
    suspend fun preload(url: String) {
        // 命中：contains 已把它挪到队尾（accessOrder），不必再走一次网络
        if (synchronized(lock) { pool.containsKey(url) }) return
        val result = runCatching {
            imageLoader.execute(ImageRequest.Builder(context).data(url).build())
        }.getOrNull()
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return
        synchronized(lock) { pool[url] = bitmap }
    }

    /** 退出阅读器时释放：引用一放，解码位图才可能被回收 */
    fun release() {
        synchronized(lock) { pool.clear() }
    }

    companion object {
        const val KEEP = 12
    }
}
