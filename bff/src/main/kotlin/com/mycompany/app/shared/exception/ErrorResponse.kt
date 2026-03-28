package com.mycompany.app.shared.exception

data class ErrorResponse(
    val error: String,
    val status: Int,
    val detail: String,
)
