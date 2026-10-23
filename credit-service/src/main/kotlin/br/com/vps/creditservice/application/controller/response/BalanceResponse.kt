package br.com.vps.creditservice.application.controller.response

import java.math.BigDecimal

data class BalanceResponse(
    val partnerId: String,
    val balance: BigDecimal
)
