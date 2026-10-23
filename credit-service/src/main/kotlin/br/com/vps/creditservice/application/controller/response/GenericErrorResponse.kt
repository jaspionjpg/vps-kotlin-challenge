package br.com.vps.creditservice.application.controller.response

import java.time.OffsetDateTime

data class GenericErrorResponse(
    val status: Int,
    val error: String,
    val message: String?,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)
