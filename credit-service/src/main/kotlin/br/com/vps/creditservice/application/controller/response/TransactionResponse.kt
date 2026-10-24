package br.com.vps.creditservice.application.controller.response

import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.TransactionStatus
import br.com.vps.creditservice.resource.model.TransactionType
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class TransactionResponse(
    val id: UUID?,
    val partnerId: UUID,
    val type: TransactionType,
    val amount: BigDecimal,
    val description: String,
    val idempotencyKey: String,
    val status: TransactionStatus,
    val failureReason: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
) {
    companion object {
        fun from(entity: TransactionEntity) = TransactionResponse(
            id = entity.id,
            partnerId = entity.partnerId,
            type = entity.type,
            amount = entity.amount,
            description = entity.description,
            idempotencyKey = entity.idempotencyKey,
            status = entity.status,
            failureReason = entity.failureReason,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
