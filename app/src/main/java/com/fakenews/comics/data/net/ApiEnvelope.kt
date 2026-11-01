package com.fakenews.comics.data.net

import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val data: T? = null,
    val message: String? = null,
)

@Serializable
data class ApiErrorBody(
    val code: Int = 0,
    val message: String? = null,
)
