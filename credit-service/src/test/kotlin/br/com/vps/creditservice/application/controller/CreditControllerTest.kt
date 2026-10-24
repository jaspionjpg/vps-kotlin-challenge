package br.com.vps.creditservice.application.controller

import br.com.vps.creditservice.domain.exception.DuplicateTransactionException
import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.service.TransactionService
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.TransactionStatus
import br.com.vps.creditservice.resource.model.TransactionType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(CreditController::class)
class CreditControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var transactionService: TransactionService

    private val partnerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val txId1 = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val txId2 = UUID.fromString("00000000-0000-0000-0000-000000000020")

    @Test
    fun `POST credit should return 201 with transaction`() {
        val transaction = TransactionEntity(
            partnerId = partnerId, type = TransactionType.CREDIT,
            amount = BigDecimal("100.00"), description = "Sale", idempotencyKey = "key-1",
            status = TransactionStatus.COMPLETED
        )
        whenever(transactionService.addCredit(eq(partnerId), any(), any(), eq("key-1"))).thenReturn(transaction)

        mockMvc.perform(
            post("/partners/$partnerId/transactions/credit")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 100.00, "description": "Sale"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("CREDIT"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.amount").value(100.00))
    }

    @Test
    fun `POST credit should return 400 when Idempotency-Key header is missing`() {
        mockMvc.perform(
            post("/partners/$partnerId/transactions/credit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 100.00, "description": "Sale"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("MissingHeader"))
    }

    @Test
    fun `POST credit should return 409 when duplicate idempotency key`() {
        whenever(transactionService.addCredit(any(), any(), any(), any()))
            .thenThrow(DuplicateTransactionException("key-dup"))

        mockMvc.perform(
            post("/partners/$partnerId/transactions/credit")
                .header("Idempotency-Key", "key-dup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 100.00, "description": "Duplicate"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("DuplicateTransactionException"))
    }

    @Test
    fun `POST credit should return 400 when amount is zero`() {
        mockMvc.perform(
            post("/partners/$partnerId/transactions/credit")
                .header("Idempotency-Key", "key-zero")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 0, "description": "Invalid"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST debit should return 201 with transaction`() {
        val transaction = TransactionEntity(
            partnerId = partnerId, type = TransactionType.DEBIT,
            amount = BigDecimal("50.00"), description = "Fee", idempotencyKey = "key-2",
            status = TransactionStatus.COMPLETED
        )
        whenever(transactionService.consumeCredit(eq(partnerId), any(), any(), eq("key-2"))).thenReturn(transaction)

        mockMvc.perform(
            post("/partners/$partnerId/transactions/debit")
                .header("Idempotency-Key", "key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 50.00, "description": "Fee"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("DEBIT"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `POST debit should return 422 when insufficient balance`() {
        whenever(transactionService.consumeCredit(any(), any(), any(), any()))
            .thenThrow(InsufficientBalanceException(partnerId))

        mockMvc.perform(
            post("/partners/$partnerId/transactions/debit")
                .header("Idempotency-Key", "key-broke")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 9999.00, "description": "Too much"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value("InsufficientBalanceException"))
    }

    @Test
    fun `GET transactions should return paginated history`() {
        val transactions = listOf(
            TransactionEntity(
                partnerId = partnerId, type = TransactionType.CREDIT,
                amount = BigDecimal("100.00"), description = "Credit 1",
                idempotencyKey = "k1", status = TransactionStatus.COMPLETED
            ),
            TransactionEntity(
                partnerId = partnerId, type = TransactionType.DEBIT,
                amount = BigDecimal("30.00"), description = "Debit 1",
                idempotencyKey = "k2", status = TransactionStatus.COMPLETED
            )
        )
        whenever(transactionService.getTransactionHistory(eq(partnerId), any()))
            .thenReturn(PageImpl(transactions))

        mockMvc.perform(get("/partners/$partnerId/transactions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
    }

    @Test
    fun `GET transaction by id should return transaction`() {
        val transaction = TransactionEntity(
            partnerId = partnerId, type = TransactionType.CREDIT,
            amount = BigDecimal("75.00"), description = "Specific",
            idempotencyKey = "k-specific", status = TransactionStatus.COMPLETED
        )
        whenever(transactionService.findById(txId1)).thenReturn(transaction)

        mockMvc.perform(get("/partners/$partnerId/transactions/$txId1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(75.00))
    }
}
