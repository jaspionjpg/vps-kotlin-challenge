package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.enums.ETransactionStatus
import br.com.vps.creditservice.resource.model.enums.ETransactionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class ReconciliationServiceTest {
    private val transactionService: TransactionService = mock()
    private val partnerService: PartnerService = mock()
    private lateinit var reconciler: TransactionReconciler

    private val partnerId = UUID.randomUUID()
    private val missingPartnerId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        reconciler = TransactionReconciler(transactionService, partnerService)
    }

    @Test
    fun `should reconcile pending CREDIT transaction`() {
        val transaction = pendingTransaction(partnerId, ETransactionType.CREDIT, "100.00", "key-1")
        val fresh = pendingTransaction(partnerId, ETransactionType.CREDIT, "100.00", "key-1", transaction.id!!)

        whenever(transactionService.findByIdForUpdate(transaction.id!!)).thenReturn(fresh)
        doNothing().whenever(partnerService).addBalance(partnerId, BigDecimal("100.00"))
        whenever(transactionService.markCompleted(any())).thenAnswer { it.arguments[0] }

        reconciler.reconcile(transaction)

        verify(partnerService).addBalance(partnerId, BigDecimal("100.00"))
        verify(transactionService).markCompleted(fresh)
    }

    @Test
    fun `should reconcile pending DEBIT transaction`() {
        val transaction = pendingTransaction(partnerId, ETransactionType.DEBIT, "50.00", "key-2")
        val fresh = pendingTransaction(partnerId, ETransactionType.DEBIT, "50.00", "key-2", transaction.id!!)

        whenever(transactionService.findByIdForUpdate(transaction.id!!)).thenReturn(fresh)
        doNothing().whenever(partnerService).subtractBalance(partnerId, BigDecimal("50.00"))
        whenever(transactionService.markCompleted(any())).thenAnswer { it.arguments[0] }

        reconciler.reconcile(transaction)

        verify(partnerService).subtractBalance(partnerId, BigDecimal("50.00"))
        verify(transactionService).markCompleted(fresh)
    }

    @Test
    fun `should mark FAILED when insufficient balance on DEBIT reconciliation`() {
        val transaction = pendingTransaction(partnerId, ETransactionType.DEBIT, "999.00", "key-3")
        val fresh = pendingTransaction(partnerId, ETransactionType.DEBIT, "999.00", "key-3", transaction.id!!)

        whenever(transactionService.findByIdForUpdate(transaction.id!!)).thenReturn(fresh)
        doThrow(InsufficientBalanceException(partnerId))
            .whenever(partnerService)
            .subtractBalance(partnerId, BigDecimal("999.00"))
        whenever(transactionService.markFailed(any(), any())).thenAnswer {
            val tx = it.arguments[0] as TransactionEntity
            tx.status = ETransactionStatus.FAILED
            tx.failureReason = it.arguments[1] as String
            tx
        }

        reconciler.reconcile(transaction)

        verify(transactionService).markFailed(fresh, "Insufficient balance during reconciliation")
    }

    @Test
    fun `should mark FAILED when partner not found`() {
        val transaction = pendingTransaction(missingPartnerId, ETransactionType.CREDIT, "10.00", "key-4")
        val fresh = pendingTransaction(missingPartnerId, ETransactionType.CREDIT, "10.00", "key-4", transaction.id!!)

        whenever(transactionService.findByIdForUpdate(transaction.id!!)).thenReturn(fresh)
        doThrow(PartnerNotFoundException(missingPartnerId))
            .whenever(partnerService)
            .addBalance(missingPartnerId, BigDecimal("10.00"))
        whenever(transactionService.markFailed(any(), any())).thenAnswer { it.arguments[0] }

        reconciler.reconcile(transaction)

        verify(transactionService).markFailed(fresh, "Partner not found during reconciliation")
    }

    @Test
    fun `should skip transaction already COMPLETED — prevents double balance apply`() {
        val transaction = pendingTransaction(partnerId, ETransactionType.CREDIT, "100.00", "key-5")
        val fresh = pendingTransaction(partnerId, ETransactionType.CREDIT, "100.00", "key-5", transaction.id!!)
        fresh.status = ETransactionStatus.COMPLETED

        whenever(transactionService.findByIdForUpdate(transaction.id!!)).thenReturn(fresh)

        reconciler.reconcile(transaction)

        verify(partnerService, never()).addBalance(any(), any())
        verify(partnerService, never()).subtractBalance(any(), any())
        verify(transactionService, never()).markCompleted(any())
        verify(transactionService, never()).markFailed(any(), any())
    }

    @Test
    fun `should skip transaction already FAILED — prevents double balance apply`() {
        val transaction = pendingTransaction(partnerId, ETransactionType.DEBIT, "50.00", "key-6")
        val fresh = pendingTransaction(partnerId, ETransactionType.DEBIT, "50.00", "key-6", transaction.id!!)
        fresh.status = ETransactionStatus.FAILED

        whenever(transactionService.findByIdForUpdate(transaction.id!!)).thenReturn(fresh)

        reconciler.reconcile(transaction)

        verify(partnerService, never()).addBalance(any(), any())
        verify(partnerService, never()).subtractBalance(any(), any())
        verify(transactionService, never()).markCompleted(any())
        verify(transactionService, never()).markFailed(any(), any())
    }

    @Test
    fun `should skip when transaction not found in DB`() {
        val transaction = pendingTransaction(partnerId, ETransactionType.CREDIT, "100.00", "key-7")

        whenever(transactionService.findByIdForUpdate(transaction.id!!)).thenReturn(null)

        reconciler.reconcile(transaction)

        verify(partnerService, never()).addBalance(any(), any())
        verify(partnerService, never()).subtractBalance(any(), any())
        verify(transactionService, never()).markCompleted(any())
        verify(transactionService, never()).markFailed(any(), any())
    }

    private fun pendingTransaction(
        partnerId: UUID,
        type: ETransactionType,
        amount: String,
        key: String,
        id: UUID = UUID.randomUUID(),
    ): TransactionEntity =
        TransactionEntity(
            id = id,
            partnerId = partnerId,
            type = type,
            amount = BigDecimal(amount),
            description = "Test transaction",
            idempotencyKey = key,
        )
}
