package br.com.vps.creditservice.application.controller.response

import java.math.BigDecimal
import java.util.UUID

data class BalanceResponse(
    val partnerId: UUID,
    val balance: BigDecimal
)
