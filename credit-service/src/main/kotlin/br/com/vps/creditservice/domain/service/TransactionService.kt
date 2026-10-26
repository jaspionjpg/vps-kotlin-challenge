package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.DuplicateTransactionException
import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.InvalidAmountException
import br.com.vps.creditservice.domain.exception.InvalidIdempotencyKeyException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.domain.exception.TransactionNotFoundException
import br.com.vps.creditservice.resource.messaging.TransactionEvent
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.enums.ETransactionStatus
import br.com.vps.creditservice.resource.model.enums.ETransactionType
import br.com.vps.creditservice.resource.repositories.TransactionRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val partnerService: PartnerService,
    private val eventPublisher: ApplicationEventPublisher,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(TransactionService::class.java)

    @Transactional
    fun addCredit(
        partnerId: UUID,
        amount: BigDecimal,
        description: String,
        idempotencyKey: String,
    ): TransactionEntity {
        validateAmount(amount)
        validateIdempotencyKey(idempotencyKey)
        checkIdempotency(idempotencyKey)

        val transaction =
            transactionRepository.save(
                TransactionEntity(
                    partnerId = partnerId,
                    type = ETransactionType.CREDIT,
                    amount = amount,
                    description = description,
                    idempotencyKey = idempotencyKey,
                ),
            )

        return try {
            partnerService.addBalance(partnerId, amount)
            markCompleted(transaction).also { publishEvent(it) }
        } catch (e: PartnerNotFoundException) {
            log.warn("Credit failed for partner $partnerId: partner not found", e)
            markFailed(transaction, "Partner not found")
        }
    }

    @Transactional
    fun consumeCredit(
        partnerId: UUID,
        amount: BigDecimal,
        description: String,
        idempotencyKey: String,
    ): TransactionEntity {
        validateAmount(amount)
        validateIdempotencyKey(idempotencyKey)
        checkIdempotency(idempotencyKey)

        val transaction =
            transactionRepository.save(
                TransactionEntity(
                    partnerId = partnerId,
                    type = ETransactionType.DEBIT,
                    amount = amount,
                    description = description,
                    idempotencyKey = idempotencyKey,
                ),
            )

        return try {
            partnerService.subtractBalance(partnerId, amount)
            markCompleted(transaction).also { publishEvent(it) }
        } catch (e: InsufficientBalanceException) {
            log.warn("Debit failed for partner $partnerId: insufficient balance", e)
            markFailed(transaction, "Insufficient balance")
        } catch (e: PartnerNotFoundException) {
            log.warn("Debit failed for partner $partnerId: partner not found", e)
            markFailed(transaction, "Partner not found")
        }
    }

    @Transactional(readOnly = true)
    fun getTransactionHistory(
        partnerId: UUID,
        pageable: Pageable,
    ): Page<TransactionEntity> {
        partnerService.findById(partnerId)
        return transactionRepository.findByPartnerIdOrderByCreatedAtDesc(partnerId, pageable)
    }

    @Transactional(readOnly = true)
    fun findById(transactionId: UUID): TransactionEntity =
        transactionRepository
            .findById(transactionId)
            .orElseThrow { TransactionNotFoundException(transactionId) }

    @Transactional(readOnly = true)
    fun findByIdForPartner(
        transactionId: UUID,
        partnerId: UUID,
    ): TransactionEntity =
        transactionRepository.findByIdAndPartnerId(transactionId, partnerId)
            ?: throw TransactionNotFoundException(transactionId)

    @Transactional(readOnly = true)
    fun findPendingBefore(
        before: OffsetDateTime,
        pageable: Pageable,
    ): List<TransactionEntity> = transactionRepository.findPendingBefore(ETransactionStatus.PENDING, before, pageable)

    @Transactional
    fun findPendingForReconciliation(
        before: OffsetDateTime,
        limit: Int,
    ): List<TransactionEntity> =
        transactionRepository.findPendingForReconciliation(
            ETransactionStatus.PENDING.name,
            before,
            limit,
        )

    @Transactional
    fun findByIdForUpdate(transactionId: UUID): TransactionEntity? = transactionRepository.findByIdForUpdate(transactionId)

    @Transactional
    fun markCompleted(transaction: TransactionEntity): TransactionEntity {
        transaction.status = ETransactionStatus.COMPLETED
        transaction.updatedAt = OffsetDateTime.now()
        return transactionRepository.save(transaction).also { recordMetric(it) }
    }

    @Transactional
    fun markFailed(
        transaction: TransactionEntity,
        reason: String,
    ): TransactionEntity {
        transaction.status = ETransactionStatus.FAILED
        transaction.failureReason = reason
        transaction.updatedAt = OffsetDateTime.now()
        return transactionRepository.save(transaction).also { recordMetric(it) }
    }

    private fun recordMetric(transaction: TransactionEntity) {
        meterRegistry
            .counter(
                "transactions.processed",
                "type",
                transaction.type.name,
                "status",
                transaction.status.name,
            ).increment()
    }

    private fun validateAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) throw InvalidAmountException()
    }

    private fun validateIdempotencyKey(idempotencyKey: String) {
        if (idempotencyKey.isBlank()) throw InvalidIdempotencyKeyException("Idempotency key must not be blank")
        if (idempotencyKey.length > 64) throw InvalidIdempotencyKeyException("Idempotency key must not exceed 64 characters")
    }

    private fun checkIdempotency(idempotencyKey: String) {
        val existing = transactionRepository.findByIdempotencyKey(idempotencyKey) ?: return
        if (existing.status == ETransactionStatus.COMPLETED) {
            throw DuplicateTransactionException(idempotencyKey)
        }
        if (existing.status == ETransactionStatus.FAILED) {
            throw DuplicateTransactionException(idempotencyKey)
        }
    }

    private fun publishEvent(transaction: TransactionEntity) {
        eventPublisher.publishEvent(
            TransactionEvent(
                transactionId = transaction.id,
                partnerId = transaction.partnerId,
                type = transaction.type,
                amount = transaction.amount,
                status = transaction.status,
                description = transaction.description,
                timestamp = transaction.updatedAt,
            ),
        )
    }
}
