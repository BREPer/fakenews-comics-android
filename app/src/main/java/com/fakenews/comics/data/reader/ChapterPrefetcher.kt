package com.fakenews.comics.data.reader

import android.content.Context
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.fakenews.comics.data.model.ChapterDto
import com.fakenews.comics.data.model.PageMetaDto
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.net.ImageUrls
import com.fakenews.comics.data.session.PageQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * 相邻章节预取：读到章末/章首附近时，先把相邻那一章的**页清单**与**首末若干张图**拉进缓存。
 *
 * 与 Web 端 `components/reader/chapterPrefetch.ts` 同一套策略。为什么要做：
 * 换章要串行做两件事——取页清单、再取首页图，实测冷缓存跨章 204ms，其中约 150ms 是
 * 「这一章首页第一次被编码」，热缓存只要 53ms。所以不在换章那一刻抢时间，
 * 而是在用户还在读本章最后几页时把成本付掉。
 *
 * 与 [PagePreloadPool] 的分工：那个管「本章前后 N 页」（长期持有的解码位图池）；
 * 这里只管跨章那一次边界，图片只入 Coil 磁盘/内存缓存，不进 LRU 池
 * ——跨章那几张图未必马上用到，塞进池里会把当前页附近的活页挤出去。
 */
@Singleton
class ChapterPrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: FakenewsApi,
    private val runner: ApiRunner,
    private val imageLoader: ImageLoader,
    private val imageUrls: ImageUrls,
) {
    private val lock = Any()

    /** 页清单内存缓存：键含 comicId，避免不同漫画的同名章节 id 撞车 */
    private val pagesCache = LinkedHashMap<String, List<PageMetaDto>>()

    /** 已发起的页清单请求（同一章被多个入口同时预取时只发一次） */
    private val inflight = HashSet<String>()

    /**
     * 读已缓存的页清单；页数与章节元数据对不上时视为失效（重新扫描会改变页数）。
     * 走过斯大林access order：命中就挪到队尾，长时间不切章也不至于把还要用的那份丢掉。
     */
    fun cachedPages(comicId: String, chapterId: String, expectCount: Int): List<PageMetaDto>? {
        synchronized(lock) {
            val hit = pagesCache[key(comicId, chapterId)] ?: return null
            return hit.takeIf { it.size == expectCount }
        }
    }

    /** 退出阅读器时释放：一部漫画的页清单只有几十 KB，但没有理由一直留着 */
    fun clear() {
        synchronized(lock) {
            pagesCache.clear()
            inflight.clear()
        }
    }

    /** 仅为了方便测试观察，不做业务判断 */
    internal fun cachedKeys(): Set<String> = synchronized(lock) { pagesCache.keys.toSet() }

    /**
     * 预取一章：页清单进内存缓存 + 首/末页图进 Coil 缓存。
     *
     * [fromStart] 决定取哪一端：往下翻要的是下一章首页，往回翻要的是上一章末页。
     * 预取失败无所谓——真换章时还有一次正常请求兜底，所以异常一律吞掉。
     */
    suspend fun prefetch(
        comicId: String,
        chapter: ChapterDto,
        quality: PageQuality,
        fromStart: Boolean,
        imageCount: Int = PREFETCH_IMAGE_COUNT,
    ) {
        fetchPages(comicId, chapter)
        val pageCount = chapter.pageCount
        if (pageCount <= 0) return
        val start = if (fromStart) 0 else max(0, pageCount - 1)
        val step = if (fromStart) 1 else -1
        for (n in 0 until imageCount) {
            val idx = start + step * n
            if (idx < 0 || idx > pageCount - 1) break
            val url = imageUrls.page(comicId, chapter.id, idx, quality) ?: break
            runCatching {
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        // 走网络也走缓存：已经在磁盘缓存里的话不必重下，只补一次 RAM 命中
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                )
            }
        }
    }

    private suspend fun fetchPages(comicId: String, chapter: ChapterDto) {
        val cacheKey = key(comicId, chapter.id)
        // 命中缓存或已有请求在飞：都不必再发一次
        val skip = synchronized(lock) {
            pagesCache.containsKey(cacheKey) || !inflight.add(cacheKey)
        }
        if (skip) return
        try {
            val pages = runner.call { api.chapterPages(comicId, chapter.id) }.pages
            synchronized(lock) {
                pagesCache[cacheKey] = pages
                inflight.remove(cacheKey)
            }
        } catch (e: CancellationException) {
            synchronized(lock) { inflight.remove(cacheKey) }
            throw e
        } catch (_: Exception) {
            synchronized(lock) { inflight.remove(cacheKey) }
        }
    }

    private fun key(comicId: String, chapterId: String) = "$comicId|$chapterId"

    companion object {
        /** 与 Web 端一致：换章后通常还会再翻一两页，所以不止取一张 */
        const val PREFETCH_IMAGE_COUNT = 2
    }
}
