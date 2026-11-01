package com.fakenews.comics.data.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 只持久化登录会话 Cookie（fakenews_session），按 host 分开存，
 * 换服务器不会把旧会话带给新地址。HttpOnly 只约束浏览器，OkHttp 需要自己保管。
 */
@Suppress("DEPRECATION")
@Singleton
class CookieStore @Inject constructor(@ApplicationContext context: Context) : CookieJar {
    private val prefs: SharedPreferences = run {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "session",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cookie = cookies.firstOrNull { it.name == COOKIE_NAME } ?: return
        val expired = cookie.value.isBlank() ||
            (cookie.expiresAt > 0 && cookie.expiresAt <= System.currentTimeMillis())
        if (expired) {
            writeRaw(url.host, null)
        } else {
            writeRaw(url.host, "${cookie.value}|${cookie.expiresAt}")
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = readRaw(url.host) ?: return emptyList()
        val parts = raw.split('|', limit = 2)
        val token = parts[0]
        val expiresAt = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        if (token.isBlank()) return emptyList()
        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
            writeRaw(url.host, null)
            return emptyList()
        }
        return listOf(
            Cookie.Builder()
                .name(COOKIE_NAME)
                .value(token)
                .hostOnlyDomain(url.host)
                .path("/")
                .httpOnly()
                .build(),
        )
    }

    fun clearAll() {
        synchronized(cacheLock) {
            cachedHost = null
            cachedRaw = null
        }
        prefs.edit().clear().apply()
    }

    /**
     * 内存里只留**最近一次命中的那条**（同一 host 的会话）。
     *
     * 图片请求也走这个 CookieJar（见 AppModule 的 ImageLoader），而 EncryptedSharedPreferences
     * 每读一次都要 AES 解密 + 可能落盘：列表滚动时一屏十几张封面 = 十几次解密，纯浪费。
     * 缓存 null 也要记住（cachedHost 命中即代表"已查过"），否则未登录时会每次请求都去读盘。
     */
    private fun readRaw(host: String): String? {
        synchronized(cacheLock) {
            if (host == cachedHost) return cachedRaw
        }
        val raw = prefs.getString(key(host), null)
        synchronized(cacheLock) {
            cachedHost = host
            cachedRaw = raw
        }
        return raw
    }

    private fun writeRaw(host: String, raw: String?) {
        synchronized(cacheLock) {
            cachedHost = host
            cachedRaw = raw
        }
        prefs.edit().apply {
            if (raw == null) remove(key(host)) else putString(key(host), raw)
        }.apply()
    }

    private fun key(host: String) = "cookie_$host"

    /** 见 [readRaw]：host 命中即视为"已查过"，避免每个请求都走一次解密的 SharedPreferences。 */
    private val cacheLock = Any()
    private var cachedHost: String? = null
    private var cachedRaw: String? = null

    companion object {
        const val COOKIE_NAME = "fakenews_session"
    }
}
