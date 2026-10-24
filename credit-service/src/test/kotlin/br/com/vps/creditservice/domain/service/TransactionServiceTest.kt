package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.DuplicateTransactionException
import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.InvalidAmountException
import br.com.vps.creditservice.resource.messaging.NotificationPublisher
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.TransactionStatus
import br.com.vps.creditservice.resource.model.TransactionType
import br.com.vps.creditservice.resource.repositories.TransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class TransactionServiceTest {

    private val transactionRepository: TransactionRepository = mock()
    private val partnerService: PartnerService = mock()
    private val notificationPublisher: NotificationPublisher = mock()
    private lateinit var transactionService: TransactionService

    private val partnerId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        transactionService = TransactionService(transactionRepository, partnerService, notificationPublisher)
    }

    @Test
    fun `addCredit should save PENDING then update to COMPLETED on success`() {
        whenever(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(null)
        whenever(transactionRepository.save(any<TransactionEntity>())).thenAnswer { it.arguments[0] }
        doNothing().whenever(partnerService).addBalance(partnerId, BigDecimal("100.00"))

        val result = transactionService.addCredit(partnerId, BigDecimal("100.00"), "Sale payment", "key-1")

        assertEquals(TransactionStatus.COMPLETED, result.status)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(BigDecimal("100.00"), result.amount)
        verify(partnerService).addBalance(partnerId, BigDecimal("100.00"))
        verify(notificationPublisher).publish(any())
    }

    @Test
    fun `addCredit should throw InvalidAmountException for zero amount`() {
        assertThrows<InvalidAmountException> {
            transactionService.addCredit(partnerId, BigDecimal.ZERO, "Invalid", "key-1")
        }
    }

    @Test
    fun `addCredit should throw InvalidAmountException for negative amount`() {
        assertThrows<InvalidAmountException> {
            transactionService.addCredit(partnerId, BigDecimal("-10.00"), "Invalid", "key-1")
        }
    }

    @Test
    fun `addCredit should throw DuplicateTransactionException for completed idempotency key`() {
        val existing = TransactionEntity(
            partnerId = partnerId, type = TransactionType.CREDIT, amount = BigDecimal("100.00"),
            description = "Already done", idempotencyKey = "key-1", status = TransactionStatus.COMPLETED
        )
        whenever(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(existing)

        assertThrows<DuplicateTransactionException> {
            transactionService.addCredit(partnerId, BigDecimal("100.00"), "Duplicate", "key-1")
        }
    }

    @Test
    fun `consumeCredit should save PENDING then update to COMPLETED on success`() {
        whenever(transactionRepository.findByIdempotencyKey("key-2")).thenReturn(null)
        whenever(transactionRepository.save(any<TransactionEntity>())).thenAnswer { it.arguments[0] }
        doNothing().whenever(partnerService).subtractBalance(partnerId, BigDecimal("50.00"))

        val result = transactionService.consumeCredit(partnerId, BigDecimal("50.00"), "Service fee", "key-2")

        assertEquals(TransactionStatus.COMPLETED, result.status)
        assertEquals(TransactionType.DEBIT, result.type)
        verify(partnerService).subtractBalance(partnerId, BigDecimal("50.00"))
    }

    @Test
    fun `consumeCredit should mark FAILED when insufficient balance`() {
        whenever(transactionRepository.findByIdempotencyKey("key-3")).thenReturn(null)
        whenever(transactionRepository.save(any<TransactionEntity>())).thenAnswer { it.arguments[0] }
        doThrow(InsufficientBalanceException(partnerId)).whenever(partnerService).subtractBalance(partnerId, BigDecimal("999.00"))

        val result = transactionService.consumeCredit(partnerId, BigDecimal("999.00"), "Too much", "key-3")

        assertEquals(TransactionStatus.FAILED, result.status)
        verify(notificationPublisher, never()).publish(any())
    }

    @Test
    fun `markCompleted should update status`() {
        val transaction = TransactionEntity(
            partnerId = partnerId, type = TransactionType.CREDIT, amount = BigDecimal("10.00"),
            description = "Test", idempotencyKey = "key-4"
        )
        whenever(transactionRepository.save(any<TransactionEntity>())).thenAnswer { it.arguments[0] }

        val result = transactionService.markCompleted(transaction)

        assertEquals(TransactionStatus.COMPLETED, result.status)
    }

    @Test
    fun `markFailed should update status and reason`() {
        val transaction = TransactionEntity(
            partnerId = partnerId, type = TransactionType.DEBIT, amount = BigDecimal("10.00"),
            description = "Test", idempotencyKey = "key-5"
        )
        whenever(transactionRepository.save(any<TransactionEntity>())).thenAnswer { it.arguments[0] }

        val result = transactionService.markFailed(transaction, "Some error")

        assertEquals(TransactionStatus.FAILED, result.status)
        assertEquals("Some error", result.failureReason)
    }
}
