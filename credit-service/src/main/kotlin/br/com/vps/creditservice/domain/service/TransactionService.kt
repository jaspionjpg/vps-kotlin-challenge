package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.DuplicateTransactionException
import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.InvalidAmountException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.domain.exception.TransactionNotFoundException
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.TransactionStatus
import br.com.vps.creditservice.resource.model.TransactionType
import br.com.vps.creditservice.resource.messaging.NotificationPublisher
import br.com.vps.creditservice.resource.messaging.TransactionEvent
import br.com.vps.creditservice.resource.repositories.TransactionRepository
import org.slf4j.LoggerFactory
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
    private val notificationPublisher: NotificationPublisher
) {

    private val log = LoggerFactory.getLogger(TransactionService::class.java)

    @Transactional
    fun addCredit(
        partnerId: UUID,
        amount: BigDecimal,
        description: String,
        idempotencyKey: String
    ): TransactionEntity {
        validateAmount(amount)
        checkIdempotency(idempotencyKey)?.let { return it }

        val transaction = transactionRepository.save(
            TransactionEntity(
                partnerId = partnerId,
                type = TransactionType.CREDIT,
                amount = amount,
                description = description,
                idempotencyKey = idempotencyKey
            )
        )

        return try {
            partnerService.addBalance(partnerId, amount)
            markCompleted(transaction).also { publishEvent(it) }
        } catch (e: PartnerNotFoundException) {
            log.warn("Credit failed for partner $partnerId: partner not found", e)
            markFailed(transaction, e.message ?: "Partner not found")
        }
    }

    @Transactional
    fun consumeCredit(
        partnerId: UUID,
        amount: BigDecimal,
        description: String,
        idempotencyKey: String
    ): TransactionEntity {
        validateAmount(amount)
        checkIdempotency(idempotencyKey)?.let { return it }

        val transaction = transactionRepository.save(
            TransactionEntity(
                partnerId = partnerId,
                type = TransactionType.DEBIT,
                amount = amount,
                description = description,
                idempotencyKey = idempotencyKey
            )
        )

        return try {
            partnerService.subtractBalance(partnerId, amount)
            markCompleted(transaction).also { publishEvent(it) }
        } catch (e: InsufficientBalanceException) {
            log.warn("Debit failed for partner $partnerId: insufficient balance", e)
            markFailed(transaction, e.message ?: "Insufficient balance")
        } catch (e: PartnerNotFoundException) {
            log.warn("Debit failed for partner $partnerId: partner not found", e)
            markFailed(transaction, e.message ?: "Partner not found")
        }
    }

    @Transactional(readOnly = true)
    fun getTransactionHistory(partnerId: UUID, pageable: Pageable): Page<TransactionEntity> {
        partnerService.findById(partnerId)
        return transactionRepository.findByPartnerIdOrderByCreatedAtDesc(partnerId, pageable)
    }

    @Transactional(readOnly = true)
    fun findById(transactionId: UUID): TransactionEntity =
        transactionRepository.findById(transactionId)
            .orElseThrow { TransactionNotFoundException(transactionId) }

    @Transactional(readOnly = true)
    fun findPendingBefore(before: OffsetDateTime, pageable: Pageable): List<TransactionEntity> =
        transactionRepository.findPendingBefore(TransactionStatus.PENDING, before, pageable)

    @Transactional
    fun markCompleted(transaction: TransactionEntity): TransactionEntity {
        transaction.status = TransactionStatus.COMPLETED
        transaction.updatedAt = OffsetDateTime.now()
        return transactionRepository.save(transaction)
    }

    @Transactional
    fun markFailed(transaction: TransactionEntity, reason: String): TransactionEntity {
        transaction.status = TransactionStatus.FAILED
        transaction.failureReason = reason
        transaction.updatedAt = OffsetDateTime.now()
        return transactionRepository.save(transaction)
    }

    private fun validateAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) throw InvalidAmountException()
    }

    private fun checkIdempotency(idempotencyKey: String): TransactionEntity? {
        val existing = transactionRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null && existing.status == TransactionStatus.COMPLETED) {
            throw DuplicateTransactionException(idempotencyKey)
        }
        return existing?.takeIf { it.status == TransactionStatus.FAILED }
    }

    private fun publishEvent(transaction: TransactionEntity) {
        notificationPublisher.publish(
            TransactionEvent(
                transactionId = transaction.id,
                partnerId = transaction.partnerId,
                type = transaction.type,
                amount = transaction.amount,
                status = transaction.status,
                description = transaction.description,
                timestamp = transaction.updatedAt
            )
        )
    }
}
