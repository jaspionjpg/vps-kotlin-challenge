package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.TransactionType
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture

@Service
class ReconciliationService(
    private val transactionService: TransactionService,
    private val partnerService: PartnerService,
    @Qualifier("reconciliationExecutor") private val executor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(ReconciliationService::class.java)

    @Value("\${app.reconciliation.pending-timeout-minutes}")
    private val pendingTimeoutMinutes: Long = 10

    companion object {
        private const val BATCH_SIZE = 100
    }

    @Scheduled(cron = "\${app.reconciliation.cron}")
    @SchedulerLock(name = "reconcilePendingTransactions", lockAtLeastFor = "1m", lockAtMostFor = "10m")
    fun reconcilePendingTransactions() {
        val cutoff = OffsetDateTime.now().minusMinutes(pendingTimeoutMinutes)
        var processed = 0

        do {
            val batch = transactionService.findPendingBefore(cutoff, PageRequest.of(0, BATCH_SIZE))
            if (batch.isEmpty()) break

            if (processed == 0) log.info("Reconciliation started")

            val futures =
                batch.map { transaction ->
                    CompletableFuture.runAsync(
                        {
                            try {
                                reconcileTransaction(transaction)
                            } catch (e: Exception) {
                                log.error("Reconciliation failed for transaction {}", transaction.id, e)
                            }
                        },
                        executor,
                    )
                }
            CompletableFuture.allOf(*futures.toTypedArray()).join()

            processed += batch.size
        } while (batch.size == BATCH_SIZE)

        if (processed > 0) log.info("Reconciliation completed: {} transactions processed", processed)
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
            transactionService.markFailed(transaction, "Partner not found during reconciliation")
            log.warn("Reconciliation FAILED for transaction {} — partner not found", transaction.id)
        }
    }
}
