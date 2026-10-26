package br.com.vps.creditservice.resource.model

import br.com.vps.creditservice.resource.model.enums.ETransactionStatus
import br.com.vps.creditservice.resource.model.enums.ETransactionType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "transaction",
    indexes = [
        Index(name = "idx_transaction_partner_id", columnList = "partnerId"),
        Index(name = "idx_transaction_idempotency_key", columnList = "idempotencyKey", unique = true),
        Index(name = "idx_transaction_status", columnList = "status"),
    ],
)
class TransactionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(nullable = false)
    val partnerId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ETransactionType,
    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,
    @Column(nullable = false)
    val description: String,
    @Column(nullable = false, unique = true)
    val idempotencyKey: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ETransactionStatus = ETransactionStatus.PENDING,
    @Column
    var failureReason: String? = null,
    @Column(nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
