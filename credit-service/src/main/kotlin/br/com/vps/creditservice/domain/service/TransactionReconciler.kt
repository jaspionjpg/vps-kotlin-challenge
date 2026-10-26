package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.enums.ETransactionStatus
import br.com.vps.creditservice.resource.model.enums.ETransactionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionReconciler(
    private val transactionService: TransactionService,
    private val partnerService: PartnerService,
) {
    private val log = LoggerFactory.getLogger(TransactionReconciler::class.java)

    @Transactional
    fun reconcile(transaction: TransactionEntity) {
        val fresh =
            transactionService.findByIdForUpdate(transaction.id!!)
                ?: run {
                    log.warn("Transaction {} not found during reconciliation, skipping", transaction.id)
                    return
                }

        if (fresh.status != ETransactionStatus.PENDING) {
            log.info(
                "Transaction {} already processed (status={}), skipping",
                transaction.id,
                fresh.status,
            )
            return
        }

        try {
            when (fresh.type) {
                ETransactionType.CREDIT -> {
                    partnerService.addBalance(fresh.partnerId, fresh.amount)
                    transactionService.markCompleted(fresh)
                    log.info("Reconciled CREDIT transaction {} for partner {}", fresh.id, fresh.partnerId)
                }

                ETransactionType.DEBIT -> {
                    partnerService.subtractBalance(fresh.partnerId, fresh.amount)
                    transactionService.markCompleted(fresh)
                    log.info("Reconciled DEBIT transaction {} for partner {}", fresh.id, fresh.partnerId)
                }
            }
        } catch (e: InsufficientBalanceException) {
            transactionService.markFailed(fresh, "Insufficient balance during reconciliation")
            log.warn("Reconciliation FAILED for DEBIT transaction {} — insufficient balance", fresh.id)
        } catch (e: PartnerNotFoundException) {
            transactionService.markFailed(fresh, "Partner not found during reconciliation")
            log.warn("Reconciliation FAILED for transaction {} — partner not found", fresh.id)
        }
    }
}
