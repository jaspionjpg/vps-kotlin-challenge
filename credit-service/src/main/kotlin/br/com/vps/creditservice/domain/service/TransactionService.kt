package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.DuplicateTransactionException
import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.InvalidAmountException
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.TransactionStatus
import br.com.vps.creditservice.resource.model.TransactionType
import br.com.vps.creditservice.resource.repositories.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val partnerService: PartnerService
) {

    private val log = LoggerFactory.getLogger(TransactionService::class.java)

    @Transactional
    fun addCredit(
        partnerId: String,
        amount: BigDecimal,
        description: String,
        idempotencyKey: String
    ): TransactionEntity {
        validateAmount(amount)
        checkIdempotency(idempotencyKey)?.let { return it }

        val partner = partnerService.findByIdForUpdate(partnerId)

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
            partner.balance = partner.balance.add(amount)
            partner.updatedAt = OffsetDateTime.now()
            transaction.status = TransactionStatus.COMPLETED
            transaction.updatedAt = OffsetDateTime.now()
            transactionRepository.save(transaction)
        } catch (e: Exception) {
            log.error("Failed to process credit for partner $partnerId", e)
            transaction.status = TransactionStatus.FAILED
            transaction.failureReason = e.message
            transaction.updatedAt = OffsetDateTime.now()
            transactionRepository.save(transaction)
        }
    }

    @Transactional
    fun consumeCredit(
        partnerId: String,
        amount: BigDecimal,
        description: String,
        idempotencyKey: String
    ): TransactionEntity {
        validateAmount(amount)
        checkIdempotency(idempotencyKey)?.let { return it }

        val partner = partnerService.findByIdForUpdate(partnerId)

        if (partner.balance < amount) {
            throw InsufficientBalanceException(partnerId)
        }

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
            partner.balance = partner.balance.subtract(amount)
            partner.updatedAt = OffsetDateTime.now()
            transaction.status = TransactionStatus.COMPLETED
            transaction.updatedAt = OffsetDateTime.now()
            transactionRepository.save(transaction)
        } catch (e: Exception) {
            log.error("Failed to process debit for partner $partnerId", e)
            transaction.status = TransactionStatus.FAILED
            transaction.failureReason = e.message
            transaction.updatedAt = OffsetDateTime.now()
            transactionRepository.save(transaction)
        }
    }

    @Transactional(readOnly = true)
    fun getTransactionHistory(partnerId: String, pageable: Pageable): Page<TransactionEntity> {
        partnerService.findById(partnerId)
        return transactionRepository.findByPartnerIdOrderByCreatedAtDesc(partnerId, pageable)
    }

    @Transactional(readOnly = true)
    fun findById(transactionId: String): TransactionEntity =
        transactionRepository.findById(transactionId)
            .orElseThrow { RuntimeException("Transaction not found: $transactionId") }

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
}
