package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.enums.ETransactionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class ReconciliationSchedulerTest {
    private val transactionService: TransactionService = mock()
    private val transactionReconciler: TransactionReconciler = mock()
    private lateinit var reconciliationService: ReconciliationService

    @BeforeEach
    fun setup() {
        reconciliationService = ReconciliationService(transactionService, transactionReconciler)
    }

    @Test
    fun `reconcilePendingTransactions should process pending transactions`() {
        val tx1 =
            TransactionEntity(
                partnerId = UUID.randomUUID(),
                type = ETransactionType.CREDIT,
                amount = BigDecimal("100.00"),
                description = "Pending 1",
                idempotencyKey = "recon-1",
            )
        val tx2 =
            TransactionEntity(
                partnerId = UUID.randomUUID(),
                type = ETransactionType.DEBIT,
                amount = BigDecimal("50.00"),
                description = "Pending 2",
                idempotencyKey = "recon-2",
            )
        whenever(transactionService.findPendingForReconciliation(any(), eq(100)))
            .thenReturn(listOf(tx1, tx2))
            .thenReturn(emptyList())

        reconciliationService.reconcilePendingTransactions()

        verify(transactionReconciler).reconcile(tx1)
        verify(transactionReconciler).reconcile(tx2)
    }

    @Test
    fun `reconcilePendingTransactions should do nothing when no pending transactions`() {
        whenever(transactionService.findPendingForReconciliation(any(), eq(100))).thenReturn(emptyList())

        reconciliationService.reconcilePendingTransactions()

        verify(transactionReconciler, never()).reconcile(any())
    }

    @Test
    fun `reconcilePendingTransactions should continue processing when one transaction fails`() {
        val tx1 =
            TransactionEntity(
                partnerId = UUID.randomUUID(),
                type = ETransactionType.CREDIT,
                amount = BigDecimal("100.00"),
                description = "Will fail",
                idempotencyKey = "recon-fail",
            )
        val tx2 =
            TransactionEntity(
                partnerId = UUID.randomUUID(),
                type = ETransactionType.CREDIT,
                amount = BigDecimal("200.00"),
                description = "Should still run",
                idempotencyKey = "recon-ok",
            )
        whenever(transactionService.findPendingForReconciliation(any(), eq(100)))
            .thenReturn(listOf(tx1, tx2))
            .thenReturn(emptyList())
        whenever(transactionReconciler.reconcile(eq(tx1))).thenThrow(RuntimeException("Unexpected"))

        reconciliationService.reconcilePendingTransactions()

        verify(transactionReconciler).reconcile(tx1)
        verify(transactionReconciler).reconcile(tx2)
    }

    @Test
    fun `reconcilePendingTransactions should process multiple batches`() {
        val largeBatch =
            (1..100).map {
                TransactionEntity(
                    partnerId = UUID.randomUUID(),
                    type = ETransactionType.CREDIT,
                    amount = BigDecimal("10.00"),
                    description = "Batch tx $it",
                    idempotencyKey = "batch-$it",
                )
            }
        val smallBatch =
            listOf(
                TransactionEntity(
                    partnerId = UUID.randomUUID(),
                    type = ETransactionType.CREDIT,
                    amount = BigDecimal("10.00"),
                    description = "Last batch tx",
                    idempotencyKey = "batch-last",
                ),
            )
        whenever(transactionService.findPendingForReconciliation(any(), eq(100)))
            .thenReturn(largeBatch)
            .thenReturn(smallBatch)
            .thenReturn(emptyList())

        reconciliationService.reconcilePendingTransactions()

        largeBatch.forEach { verify(transactionReconciler).reconcile(it) }
        smallBatch.forEach { verify(transactionReconciler).reconcile(it) }
    }
}
