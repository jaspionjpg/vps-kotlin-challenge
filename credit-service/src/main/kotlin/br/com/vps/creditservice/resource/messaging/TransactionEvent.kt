package br.com.vps.creditservice.resource.messaging

import br.com.vps.creditservice.resource.model.enums.ETransactionStatus
import br.com.vps.creditservice.resource.model.enums.ETransactionType
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class TransactionEvent(
    val transactionId: UUID?,
    val partnerId: UUID,
    val type: ETransactionType,
    val amount: BigDecimal,
    val status: ETransactionStatus,
    val description: String,
    val timestamp: OffsetDateTime,
)
