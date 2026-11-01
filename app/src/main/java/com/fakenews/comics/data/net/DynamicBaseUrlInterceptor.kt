package com.fakenews.comics.data.net

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retrofit 的 baseUrl 固定为占位地址，真正的服务器由这里在请求发出前替换。
 * 这样切换服务器不需要重建 Retrofit / OkHttp（CookieJar 也能按最终 host 匹配）。
 */
class DynamicBaseUrlInterceptor(
    private val baseUrl: () -> HttpUrl?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = baseUrl() ?: throw IOException("尚未设置服务器地址")
        val request = chain.request()
        val old = request.url
        val basePath = base.encodedPath.trimEnd('/')
        val newPath = (basePath + old.encodedPath).ifEmpty { "/" }
        val url = old.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(newPath)
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
