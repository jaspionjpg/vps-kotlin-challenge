package br.com.vps.creditservice.domain.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ReconciliationService(
    private val transactionService: TransactionService,
    private val transactionReconciler: TransactionReconciler,
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
            val batch = transactionService.findPendingForReconciliation(cutoff, BATCH_SIZE)
            if (batch.isEmpty()) break

            if (processed == 0) log.info("Reconciliation started")

            batch.forEach { transaction ->
                try {
                    transactionReconciler.reconcile(transaction)
                } catch (e: Exception) {
                    log.error("Reconciliation failed for transaction {}", transaction.id, e)
                }
            }

            processed += batch.size
        } while (batch.size == BATCH_SIZE)

        if (processed > 0) log.info("Reconciliation completed: {} transactions processed", processed)
    }
}
