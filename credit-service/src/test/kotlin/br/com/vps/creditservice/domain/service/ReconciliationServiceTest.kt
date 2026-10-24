package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.TransactionStatus
import br.com.vps.creditservice.resource.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class ReconciliationServiceTest {

    private val transactionService: TransactionService = mock()
    private val partnerService: PartnerService = mock()
    private lateinit var reconciliationService: ReconciliationService

    private val partnerId = UUID.randomUUID()
    private val missingPartnerId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        reconciliationService = ReconciliationService(transactionService, partnerService)
    }

    @Test
    fun `should reconcile pending CREDIT transaction`() {
        val transaction = TransactionEntity(
            partnerId = partnerId, type = TransactionType.CREDIT, amount = BigDecimal("100.00"),
            description = "Pending credit", idempotencyKey = "key-1"
        )
        doNothing().whenever(partnerService).addBalance(partnerId, BigDecimal("100.00"))
        whenever(transactionService.markCompleted(any())).thenAnswer { it.arguments[0] }

        reconciliationService.reconcileTransaction(transaction)

        verify(partnerService).addBalance(partnerId, BigDecimal("100.00"))
        verify(transactionService).markCompleted(transaction)
    }

    @Test
    fun `should reconcile pending DEBIT transaction`() {
        val transaction = TransactionEntity(
            partnerId = partnerId, type = TransactionType.DEBIT, amount = BigDecimal("50.00"),
            description = "Pending debit", idempotencyKey = "key-2"
        )
        doNothing().whenever(partnerService).subtractBalance(partnerId, BigDecimal("50.00"))
        whenever(transactionService.markCompleted(any())).thenAnswer { it.arguments[0] }

        reconciliationService.reconcileTransaction(transaction)

        verify(partnerService).subtractBalance(partnerId, BigDecimal("50.00"))
        verify(transactionService).markCompleted(transaction)
    }

    @Test
    fun `should mark FAILED when insufficient balance on DEBIT reconciliation`() {
        val transaction = TransactionEntity(
            partnerId = partnerId, type = TransactionType.DEBIT, amount = BigDecimal("999.00"),
            description = "Too much", idempotencyKey = "key-3"
        )
        doThrow(InsufficientBalanceException(partnerId)).whenever(partnerService).subtractBalance(partnerId, BigDecimal("999.00"))
        whenever(transactionService.markFailed(any(), any())).thenAnswer {
            val tx = it.arguments[0] as TransactionEntity
            tx.status = TransactionStatus.FAILED
            tx.failureReason = it.arguments[1] as String
            tx
        }

        reconciliationService.reconcileTransaction(transaction)

        verify(transactionService).markFailed(transaction, "Insufficient balance during reconciliation")
    }

    @Test
    fun `should mark FAILED when partner not found`() {
        val transaction = TransactionEntity(
            partnerId = missingPartnerId, type = TransactionType.CREDIT, amount = BigDecimal("10.00"),
            description = "Ghost partner", idempotencyKey = "key-4"
        )
        doThrow(PartnerNotFoundException(missingPartnerId)).whenever(partnerService).addBalance(missingPartnerId, BigDecimal("10.00"))
        whenever(transactionService.markFailed(any(), any())).thenAnswer { it.arguments[0] }

        reconciliationService.reconcileTransaction(transaction)

        verify(transactionService).markFailed(transaction, "Partner not found: $missingPartnerId")
    }
}
