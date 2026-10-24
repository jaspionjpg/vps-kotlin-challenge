package br.com.vps.creditservice.application.controller.request

import jakarta.validation.constraints.NotBlank

data class CreatePartnerRequest(
    @field:NotBlank
    val externalId: String,
    @field:NotBlank
    val name: String,
)
