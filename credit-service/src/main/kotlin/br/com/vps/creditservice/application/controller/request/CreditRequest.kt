package br.com.vps.creditservice.application.controller.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreditRequest(
    @field:DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    val amount: BigDecimal,
    @field:NotBlank
    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    val description: String,
)
