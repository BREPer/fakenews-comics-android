package com.fakenews.comics.data.net

class ApiException(
    val status: Int,
    override val message: String,
) : Exception(message)
