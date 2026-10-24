package br.com.vps.creditservice.resource.repositories

import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.TransactionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

import java.util.UUID

interface TransactionRepository : JpaRepository<TransactionEntity, UUID> {

    fun findByIdempotencyKey(idempotencyKey: String): TransactionEntity?

    fun findByPartnerIdOrderByCreatedAtDesc(partnerId: UUID, pageable: Pageable): Page<TransactionEntity>

    @Query(
        "SELECT t FROM TransactionEntity t WHERE t.status = :status AND t.createdAt < :before"
    )
    fun findPendingBefore(
        @Param("status") status: TransactionStatus,
        @Param("before") before: OffsetDateTime
    ): List<TransactionEntity>
}
