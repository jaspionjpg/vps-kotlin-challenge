package br.com.vps.creditservice.application.controller.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

data class CreditRequest(
    @field:DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    val amount: BigDecimal,
    @field:NotBlank
    val description: String,
)
