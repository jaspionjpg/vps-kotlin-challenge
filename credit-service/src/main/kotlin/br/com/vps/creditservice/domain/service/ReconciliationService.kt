package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.TransactionType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class ReconciliationService(
    private val transactionService: TransactionService,
    private val partnerService: PartnerService
) {

    private val log = LoggerFactory.getLogger(ReconciliationService::class.java)

    @Value("\${app.reconciliation.pending-timeout-minutes}")
    private val pendingTimeoutMinutes: Long = 10

    @Scheduled(cron = "\${app.reconciliation.cron}")
    fun reconcilePendingTransactions() {
        val cutoff = OffsetDateTime.now().minusMinutes(pendingTimeoutMinutes)
        val pendingTransactions = transactionService.findPendingBefore(cutoff)

        if (pendingTransactions.isEmpty()) return

        log.info("Reconciliation started: {} pending transactions found", pendingTransactions.size)

        pendingTransactions.forEach { transaction ->
            try {
                reconcileTransaction(transaction)
            } catch (e: Exception) {
                log.error("Reconciliation failed for transaction {}", transaction.id, e)
            }
        }

        log.info("Reconciliation completed")
    }

    @Transactional
    fun reconcileTransaction(transaction: TransactionEntity) {
        try {
            when (transaction.type) {
                TransactionType.CREDIT -> {
                    partnerService.addBalance(transaction.partnerId, transaction.amount)
                    transactionService.markCompleted(transaction)
                    log.info("Reconciled CREDIT transaction {} for partner {}", transaction.id, transaction.partnerId)
                }
                TransactionType.DEBIT -> {
                    partnerService.subtractBalance(transaction.partnerId, transaction.amount)
                    transactionService.markCompleted(transaction)
                    log.info("Reconciled DEBIT transaction {} for partner {}", transaction.id, transaction.partnerId)
                }
            }
        } catch (e: InsufficientBalanceException) {
            transactionService.markFailed(transaction, "Insufficient balance during reconciliation")
            log.warn("Reconciliation FAILED for DEBIT transaction {} — insufficient balance", transaction.id)
        } catch (e: PartnerNotFoundException) {
            transactionService.markFailed(transaction, "Partner not found: ${transaction.partnerId}")
            log.warn("Reconciliation FAILED for transaction {} — partner not found", transaction.id)
        }
    }
}
