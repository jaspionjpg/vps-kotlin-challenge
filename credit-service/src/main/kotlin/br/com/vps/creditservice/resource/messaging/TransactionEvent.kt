package br.com.vps.creditservice.resource.messaging

import br.com.vps.creditservice.resource.model.TransactionStatus
import br.com.vps.creditservice.resource.model.TransactionType
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class TransactionEvent(
    val transactionId: UUID?,
    val partnerId: UUID,
    val type: TransactionType,
    val amount: BigDecimal,
    val status: TransactionStatus,
    val description: String,
    val timestamp: OffsetDateTime
)
