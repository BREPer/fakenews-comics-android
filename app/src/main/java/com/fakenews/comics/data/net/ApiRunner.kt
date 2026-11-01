package com.fakenews.comics.data.net

import com.fakenews.comics.data.session.SessionBus
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 信封解包 + 错误归一化。服务端成功响应是 {code:0,data}，
 * 失败可能是非 2xx（401/429）或 200 + code!=0，这里统一成 ApiException。
 */
@Singleton
class ApiRunner @Inject constructor(
    private val json: Json,
    private val sessionBus: SessionBus,
) {
    suspend fun <T> call(block: suspend () -> Response<ApiEnvelope<T>>): T {
        val response = try {
            block()
        } catch (e: SocketTimeoutException) {
            throw ApiException(0, "请求超时：服务器可能正在忙（扫描/转码），稍后重试")
        } catch (e: IOException) {
            val msg = e.message
            throw ApiException(
                0,
                if (msg != null && msg.contains("尚未设置")) msg else "无法连接服务器，请确认 NAS 上的应用正在运行",
            )
        } catch (e: SerializationException) {
            // 地址指错（打到反向代理/网盘的错误页）、服务端版本不匹配、响应被截断都会走到这里。
            // 不接住的话调用方只能拿到 "Unexpected JSON token at offset 0" 这种原文当提示。
            throw ApiException(0, "服务器返回的内容无法解析，请确认地址指向的是肥牛漫画服务")
        }

        val envelope = response.body()
        if (!response.isSuccessful || (envelope != null && envelope.code != 0)) {
            if (response.code() == 401) sessionBus.notifyUnauthorized()
            val message = envelope?.message
                ?: parseErrorMessage(response)
                ?: "请求失败 (${response.code()})"
            throw ApiException(response.code(), message)
        }
        return envelope?.data ?: throw ApiException(response.code(), "响应缺少 data")
    }

    private fun parseErrorMessage(response: Response<*>): String? {
        val text = response.errorBody()?.string() ?: return null
        return runCatching { json.decodeFromString<ApiErrorBody>(text).message }.getOrNull()
    }
}
