package com.fakenews.comics.data.upload

import androidx.compose.runtime.Immutable
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.fakenews.comics.data.model.UploadResultDto
import com.fakenews.comics.data.net.ApiEnvelope
import com.fakenews.comics.data.net.ApiErrorBody
import com.fakenews.comics.data.net.ApiException
import com.fakenews.comics.data.session.SessionBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** 用户从系统文件 picker 里选中的一个待传文件。 */
@Immutable
data class PickedFile(
    val uri: Uri,
    val name: String,
    /** picker 给的字节数；拿不到时是 -1（未知时界面把那部分按 0 计入） */
    val sizeBytes: Long,
)

/** 章节型只收压缩包（服务端按包建文件夹解压）；单篇型另收 epub / pdf。与 Web 的表同源。 */
val CHAPTER_EXTS: Set<String> = setOf("cbz", "zip", "tar", "cbt", "rar", "cbr")

/** Web 的 SINGLE_EXTS 含 7z/cb7（能上传但暂不支持阅读），保持一致。 */
val SINGLE_EXTS: Set<String> = CHAPTER_EXTS + setOf("7z", "cb7", "epub", "pdf")

fun acceptedExtensions(isChapter: Boolean): Set<String> = if (isChapter) CHAPTER_EXTS else SINGLE_EXTS

fun extensionOf(name: String): String {
    val i = name.lastIndexOf('.')
    return if (i > 0) name.substring(i + 1).lowercase() else ""
}

/** 取 picker 返回 Uri 的展示名与大小；取不到名字时用路径最后一段兜底。 */
fun ContentResolver.readPickedFile(uri: Uri): PickedFile {
    var name: String? = null
    var size = -1L
    runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0) name = c.getString(ni)
                    if (si >= 0) size = c.getLong(si)
                }
            }
    }
    val fallback = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    return PickedFile(uri, name?.takeIf { it.isNotBlank() } ?: fallback, size)
}

/**
 * 上传到漫画库（对齐 Web `UploadSheet` + `POST /api/libraries/{id}/upload`）。
 *
 * 为什么不走 Retrofit：这里的超时口径和别处不一样。共享客户端是 connect 10s /
 * read 60s / write 30s —— 章节型服务端收完还要**解压**再回信封，几百 MB 的包
 * 传输 + 解压很容易超过 60s；而全局放宽会连累所有请求（服务器挂了要等两分钟
 * 才报错）。所以这里用 `newBuilder()` 复制一份**继承了 CookieJar 与动态地址拦截器**
 * 的客户端，只把 read/write 放宽到分钟级。
 *
 * 另外也不整体把文件读进内存：App 进程堆上限通常只有几百 MB，几百 MB 的 byte[] 必 OOM。
 * 从 contentResolver 边读边写（Web 那边是 `<input type=file>` + XHR，同理）。
 */
@Singleton
class LibraryUploader @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val sessionBus: SessionBus,
) {
    /**
     * 上传 [files]。[onProgress] 会在 **IO 线程**被调用，参数是累计已发送字节数
     * （调用方别在里面做 UI 操作；写 StateFlow 的 value 是线程安全的，可以直接写）。
     *
     * 取消：外层协程被 cancel 时，正在进行的传输会在下一个分片处抛 IOException 收尾，
     * 不留后台请求。
     */
    suspend fun upload(
        libraryId: String,
        files: List<PickedFile>,
        onProgress: (sentBytes: Long) -> Unit = {},
    ): UploadResultDto = withContext(Dispatchers.IO) {
        val cancelled = AtomicBoolean(false)
        coroutineContext[Job]?.invokeOnCompletion { cancelled.set(true) }
        val sent = AtomicLong(0)
        val resolver = appContext.contentResolver

        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            files.forEach { file ->
                addFormDataPart(
                    name = "files",
                    filename = file.name,
                    body = StreamPartBody(resolver, file, sent, cancelled, onProgress),
                )
            }
        }.build()

        val request = Request.Builder()
            // 占位地址：DynamicBaseUrlInterceptor 会在发出前换成真实服务器 + 反代子路径
            .url("http://127.0.0.1/api/libraries/${Uri.encode(libraryId)}/upload")
            .post(body)
            .build()

        longTimeoutClient().newCall(request).execute().use { parse(it) }
    }

    /** 长超时客户端：只放宽 read/write，其余（Cookie、动态地址、连接超时）照抄。 */
    private fun longTimeoutClient(): OkHttpClient = client.newBuilder()
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    private fun parse(res: Response): UploadResultDto {
        val text = res.body?.string().orEmpty()
        val envelope = runCatching {
            json.decodeFromString<ApiEnvelope<UploadResultDto>>(text)
        }.getOrNull()
        if (!res.isSuccessful || (envelope != null && envelope.code != 0)) {
            if (res.code == 401) sessionBus.notifyUnauthorized()
            val message = envelope?.message
                ?: runCatching { json.decodeFromString<ApiErrorBody>(text).message }.getOrNull()
                ?: "上传失败 (${res.code})"
            throw ApiException(res.code, message)
        }
        return envelope?.data ?: throw ApiException(res.code, "响应缺少 data")
    }
}

/**
 * 一个 multipart part：从 contentResolver 的 InputStream 边读边写，顺便累计字节数。
 *
 * [contentLength] 尽量给准确值：不给会退化成分块传输（服务端 multipart 解析能读，
 * 但个别反代不喜欢）；给了还能让 OkHttp 在文件被截断时立刻报错，而不是传到最后才发现。
 * [cancelled] 由外层协程的完成回调置位 —— 用来实现「取消上传」这个 Web 也有的动作。
 */
private class StreamPartBody(
    private val resolver: ContentResolver,
    private val file: PickedFile,
    private val sent: AtomicLong,
    private val cancelled: AtomicBoolean,
    private val onProgress: (Long) -> Unit,
) : RequestBody() {
    override fun contentType() = "application/octet-stream".toMediaTypeOrNull()

    override fun contentLength(): Long = if (file.sizeBytes > 0) file.sizeBytes else -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(file.uri)
            ?: throw IOException("无法读取文件：${file.name}")
        input.use { stream ->
            val buf = ByteArray(CHUNK)
            while (true) {
                if (cancelled.get()) throw IOException("上传已取消")
                val n = stream.read(buf)
                if (n < 0) break
                sink.write(buf, 0, n)
                val total = sent.addAndGet(n.toLong())
                onProgress(total)
            }
        }
    }

    private companion object {
        /** 8KB：与 Okio 默认缓冲同阶，再大只是白白多占一份内存。 */
        const val CHUNK = 8 * 1024
    }
}
