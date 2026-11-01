package com.fakenews.comics.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(
    val name: String = "",
    val ok: Boolean = false,
    val ts: Long = 0,
    val version: String = "",
)

@Serializable
data class AuthStateDto(
    val enabled: Boolean = false,
    val authenticated: Boolean = false,
    val username: String? = null,
    val hasCredential: Boolean = false,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResultDto(
    val ok: Boolean = false,
    val username: String? = null,
)

@Serializable
data class OkDto(
    val ok: Boolean = false,
)

/** 服务端契约主版本；与 /api/health 的 version 比较，只提示不阻断。 */
const val SUPPORTED_SERVER_MAJOR = 2

fun majorOf(version: String?): Int? =
    version?.substringBefore('.')?.toIntOrNull()

fun versionWarning(serverVersion: String?): String? {
    val major = majorOf(serverVersion) ?: return null
    if (major == SUPPORTED_SERVER_MAJOR) return null
    return "客户端按服务端 v$SUPPORTED_SERVER_MAJOR.x 契约开发，当前服务端为 v$serverVersion，部分功能可能不可用。"
}
