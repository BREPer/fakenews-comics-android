package com.fakenews.comics.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务器地址：局域网常用 http，允许用户省略 scheme。
 * 保留反代子路径（如 http://nas:13800/comics/），请求时由拦截器拼接。
 */
@Singleton
class ServerStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    @Volatile
    var baseUrl: HttpUrl? = prefs.getString(KEY, null)?.let(::normalize)
        private set

    val savedUrl: String?
        get() = baseUrl?.toString()?.removeSuffix("/")

    /** 返回规范化后的展示地址（无结尾斜杠）。 */
    fun save(input: String): String {
        val normalized = normalize(input) ?: throw IllegalArgumentException("服务器地址无效，例如 http://192.168.1.10:13800")
        prefs.edit().putString(KEY, normalized.toString()).apply()
        baseUrl = normalized
        return normalized.toString().removeSuffix("/")
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        baseUrl = null
    }

    companion object {
        private const val KEY = "base_url"

        fun normalize(input: String): HttpUrl? {
            var text = input.trim()
            if (text.isEmpty()) return null
            if (!text.startsWith("http://", ignoreCase = true) &&
                !text.startsWith("https://", ignoreCase = true)
            ) {
                text = "http://$text"
            }
            val url = text.toHttpUrlOrNull() ?: return null
            val path = url.encodedPath.trimEnd('/') + "/"
            return url.newBuilder()
                .encodedPath(path)
                .query(null)
                .fragment(null)
                .build()
        }
    }
}
