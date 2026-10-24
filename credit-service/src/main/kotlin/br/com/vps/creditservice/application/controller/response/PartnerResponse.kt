package br.com.vps.creditservice.application.controller.response

import br.com.vps.creditservice.resource.model.PartnerEntity
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class PartnerResponse(
    val id: UUID?,
    val externalId: String,
    val name: String,
    val balance: BigDecimal,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
) {
    companion object {
        fun from(entity: PartnerEntity) = PartnerResponse(
            id = entity.id,
            externalId = entity.externalId,
            name = entity.name,
            balance = entity.balance,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
