package com.fakenews.comics.data.net

import com.fakenews.comics.data.model.AboutDto
import com.fakenews.comics.data.model.AuthEnabledBody
import com.fakenews.comics.data.model.AuthEnabledDto
import com.fakenews.comics.data.model.AuthStateDto
import com.fakenews.comics.data.model.BackupDto
import com.fakenews.comics.data.model.CacheStatsDto
import com.fakenews.comics.data.model.ClearCacheDto
import com.fakenews.comics.data.model.ComicDetailDto
import com.fakenews.comics.data.model.ComicDto
import com.fakenews.comics.data.model.CredentialsBody
import com.fakenews.comics.data.model.EvictCacheDto
import com.fakenews.comics.data.model.FacetsDto
import com.fakenews.comics.data.model.FavoriteBody
import com.fakenews.comics.data.model.HealthDto
import com.fakenews.comics.data.model.HistoryPageDto
import com.fakenews.comics.data.model.ImportResultDto
import com.fakenews.comics.data.model.ItemsDto
import com.fakenews.comics.data.model.LibraryBody
import com.fakenews.comics.data.model.LibraryBrowseDto
import com.fakenews.comics.data.model.LibraryCreateDto
import com.fakenews.comics.data.model.LibraryDeleteDto
import com.fakenews.comics.data.model.LibraryDto
import com.fakenews.comics.data.model.LibraryPatchBody
import com.fakenews.comics.data.model.LoginRequest
import com.fakenews.comics.data.model.LoginResultDto
import com.fakenews.comics.data.model.OkDto
import com.fakenews.comics.data.model.PagedDto
import com.fakenews.comics.data.model.PagesDto
import com.fakenews.comics.data.model.PutProgressBody
import com.fakenews.comics.data.model.PutProgressResult
import com.fakenews.comics.data.model.RescanResultDto
import com.fakenews.comics.data.model.ScanBody
import com.fakenews.comics.data.model.ScanStartDto
import com.fakenews.comics.data.model.ScanStatusDto
import com.fakenews.comics.data.model.SettingsDto
import com.fakenews.comics.data.model.UpdateComicBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface FakenewsApi {
    @GET("api/health")
    suspend fun health(): Response<ApiEnvelope<HealthDto>>

    @GET("api/auth/status")
    suspend fun authStatus(): Response<ApiEnvelope<AuthStateDto>>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<LoginResultDto>>

    @POST("api/auth/logout")
    suspend fun logout(): Response<ApiEnvelope<OkDto>>

    @PUT("api/auth/credentials")
    suspend fun setCredentials(@Body body: CredentialsBody): Response<ApiEnvelope<OkDto>>

    @PUT("api/auth/enabled")
    suspend fun setAuthEnabled(@Body body: AuthEnabledBody): Response<ApiEnvelope<AuthEnabledDto>>

    // ---- 漫画 ----
    @GET("api/comics")
    suspend fun listComics(
        @Query("q") q: String? = null,
        @Query("libraryId") libraryId: String? = null,
        @Query("status") status: String? = null,
        @Query("format") format: String? = null,
        @Query("tag") tag: String? = null,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 36,
    ): Response<ApiEnvelope<PagedDto<ComicDto>>>

    @GET("api/comics/facets")
    suspend fun facets(): Response<ApiEnvelope<FacetsDto>>

    @GET("api/comics/{comicId}")
    suspend fun comicDetail(@Path("comicId") comicId: String): Response<ApiEnvelope<ComicDetailDto>>

    /** 编辑元数据（标题/作者/简介/标签）；服务端会把这条记录置为 meta_locked，重扫不再覆盖。 */
    @PUT("api/comics/{comicId}")
    suspend fun updateComic(
        @Path("comicId") comicId: String,
        @Body body: UpdateComicBody,
    ): Response<ApiEnvelope<OkDto>>

    /** 单部重扫：替换文件后不必整库重来 */
    @POST("api/comics/{comicId}/scan")
    suspend fun rescanComic(@Path("comicId") comicId: String): Response<ApiEnvelope<RescanResultDto>>

    @GET("api/comics/{comicId}/chapters/{chapterId}/pages")
    suspend fun chapterPages(
        @Path("comicId") comicId: String,
        @Path("chapterId") chapterId: String,
    ): Response<ApiEnvelope<PagesDto>>

    // ---- 收藏 / 历史 ----
    @GET("api/favorites")
    suspend fun favorites(): Response<ApiEnvelope<ItemsDto<ComicDto>>>

    @POST("api/favorites")
    suspend fun addFavorite(@Body body: FavoriteBody): Response<ApiEnvelope<OkDto>>

    @DELETE("api/favorites/{comicId}")
    suspend fun removeFavorite(@Path("comicId") comicId: String): Response<ApiEnvelope<OkDto>>

    @GET("api/history")
    suspend fun history(
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): Response<ApiEnvelope<HistoryPageDto>>

    @DELETE("api/history/{comicId}")
    suspend fun removeHistory(@Path("comicId") comicId: String): Response<ApiEnvelope<OkDto>>

    // ---- 进度 ----
    @PUT("api/progress")
    suspend fun putProgress(@Body body: PutProgressBody): Response<ApiEnvelope<PutProgressResult>>

    @POST("api/progress/{comicId}/reset")
    suspend fun resetProgress(@Path("comicId") comicId: String): Response<ApiEnvelope<OkDto>>

    // ---- 服务端设置（只剩自动扫描间隔与缓存上限）----
    @GET("api/settings")
    suspend fun getSettings(): Response<ApiEnvelope<SettingsDto>>

    @PUT("api/settings")
    suspend fun putSettings(@Body body: SettingsDto): Response<ApiEnvelope<OkDto>>

    // ---- 缓存管理 ----
    @GET("api/cache/stats")
    suspend fun cacheStats(): Response<ApiEnvelope<CacheStatsDto>>

    @POST("api/cache/clear")
    suspend fun clearCache(): Response<ApiEnvelope<ClearCacheDto>>

    @POST("api/cache/evict")
    suspend fun evictCache(): Response<ApiEnvelope<EvictCacheDto>>

    // ---- 扫描状态（首页横幅 + 扫描结束后自动刷新）----
    @GET("api/scan/status")
    suspend fun scanStatus(): Response<ApiEnvelope<ScanStatusDto>>

    /** 触发扫描。不传 [ScanBody.libraryId] 即全库扫描；`started=false` 表示已有扫描在跑。 */
    @POST("api/scan")
    suspend fun triggerScan(@Body body: ScanBody = ScanBody()): Response<ApiEnvelope<ScanStartDto>>

    // ---- 漫画库（2026-10-28 起与 Web 全量对齐：新建 / 编辑 / 删除 / 目录点选）----
    @GET("api/libraries")
    suspend fun libraries(): Response<ApiEnvelope<ItemsDto<LibraryDto>>>

    /**
     * 目录点选器的一层。`path` 缺省 = 挂载根。
     *
     * 为什么要有它：容器内恒定只见 `/comics`，用户却拿着飞牛的宿主机路径
     * （`/vol1/...`）来填，扫完书架是空的还要排查半天。从根逐级下钻给出的路径
     * 天然可访问，整类错误在 UI 层消失。
     */
    @GET("api/libraries/browse")
    suspend fun browseLibraryDirs(
        @Query("path") path: String? = null,
    ): Response<ApiEnvelope<LibraryBrowseDto>>

    /** 新建；服务端写入后立即触发该库扫描。返回新库 id。 */
    @POST("api/libraries")
    suspend fun createLibrary(@Body body: LibraryBody): Response<ApiEnvelope<LibraryCreateDto>>

    /**
     * 编辑。Retrofit 没有 PATCH 注解，用 @HTTP 显式指定。
     * 只发改过的字段（见 [LibraryPatchBody]），服务端同样会trigger一次扫描。
     */
    @HTTP(method = "PATCH", path = "api/libraries/{id}", hasBody = true)
    suspend fun updateLibrary(
        @Path("id") id: String,
        @Body body: LibraryPatchBody,
    ): Response<ApiEnvelope<OkDto>>

    /** 删除：级联清掉库内漫画的阅读进度 / 收藏 / 已读标记（磁盘原文件不动）。 */
    @DELETE("api/libraries/{id}")
    suspend fun deleteLibrary(
        @Path("id") id: String,
    ): Response<ApiEnvelope<LibraryDeleteDto>>

    // ---- 阅读数据备份 ----
    @GET("api/backup/export")
    suspend fun exportBackup(): Response<ApiEnvelope<BackupDto>>

    @POST("api/backup/import")
    suspend fun importBackup(@Body body: BackupDto): Response<ApiEnvelope<ImportResultDto>>

    // ---- 关于 ----
    @GET("api/about")
    suspend fun about(): Response<ApiEnvelope<AboutDto>>
}
