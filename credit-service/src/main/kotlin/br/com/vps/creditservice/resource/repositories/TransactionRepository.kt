package br.com.vps.creditservice.resource.repositories

import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.enums.ETransactionStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime
import java.util.UUID

interface TransactionRepository : JpaRepository<TransactionEntity, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): TransactionEntity?

    fun findByIdAndPartnerId(
        id: UUID,
        partnerId: UUID,
    ): TransactionEntity?

    fun findByPartnerIdOrderByCreatedAtDesc(
        partnerId: UUID,
        pageable: Pageable,
    ): Page<TransactionEntity>

    @Query(
        "SELECT t FROM TransactionEntity t WHERE t.status = :status AND t.createdAt < :before ORDER BY t.createdAt ASC",
    )
    fun findPendingBefore(
        @Param("status") status: ETransactionStatus,
        @Param("before") before: OffsetDateTime,
        pageable: Pageable,
    ): List<TransactionEntity>

    @Query(
        value = """
            SELECT * FROM transaction
            WHERE status = :status AND created_at < :before
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findPendingForReconciliation(
        @Param("status") status: String,
        @Param("before") before: OffsetDateTime,
        @Param("limit") limit: Int,
    ): List<TransactionEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
        jakarta.persistence.QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "0",
        ),
    )
    @Query("SELECT t FROM TransactionEntity t WHERE t.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: UUID,
    ): TransactionEntity?
}
