package br.com.vps.creditservice.integration

import br.com.vps.creditservice.domain.exception.DuplicateTransactionException
import br.com.vps.creditservice.domain.service.BalanceCacheService
import br.com.vps.creditservice.domain.service.PartnerService
import br.com.vps.creditservice.domain.service.TransactionService
import br.com.vps.creditservice.resource.messaging.NotificationPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.services.sns.SnsClient
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=" +
            "io.awspring.cloud.autoconfigure.sns.SnsAutoConfiguration," +
            "io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration," +
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration," +
            "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration",
        "app.security.api-key=test-key",
    ],
)
@Testcontainers
@Tag("integration")
class IdempotencyTest {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:15.3")
    }

    @MockitoBean
    private lateinit var snsClient: SnsClient

    @MockitoBean
    private lateinit var balanceCacheService: BalanceCacheService

    @MockitoBean
    private lateinit var notificationPublisher: NotificationPublisher

    @MockitoBean
    private lateinit var redisTemplate: org.springframework.data.redis.core.RedisTemplate<String, String>

    @Autowired
    private lateinit var partnerService: PartnerService

    @Autowired
    private lateinit var transactionService: TransactionService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun queryBalance(partnerId: UUID): BigDecimal =
        jdbcTemplate.queryForObject("SELECT balance FROM partner WHERE id = ?", BigDecimal::class.java, partnerId)!!

    private fun countTransactions(idempotencyKey: String): Long =
        jdbcTemplate.queryForObject("SELECT count(*) FROM transaction WHERE idempotency_key = ?", Long::class.java, idempotencyKey)!!

    @Test
    fun `duplicate credit with same idempotency key should throw and not duplicate balance`() {
        val partnerId = partnerService.createPartner(UUID.randomUUID().toString(), "Idemp Credit").id!!

        transactionService.addCredit(partnerId, BigDecimal("100.00"), "First", "idem-credit-1")

        assertThrows<DuplicateTransactionException> {
            transactionService.addCredit(partnerId, BigDecimal("100.00"), "Duplicate", "idem-credit-1")
        }

        assertEquals(
            0,
            BigDecimal("100.00").compareTo(queryBalance(partnerId)),
            "Balance should be 100 — second credit must not have been applied",
        )
        assertEquals(
            1,
            countTransactions("idem-credit-1"),
            "Only one transaction record should exist",
        )
    }

    @Test
    fun `duplicate debit with same idempotency key should throw and not duplicate deduction`() {
        val partnerId = partnerService.createPartner(UUID.randomUUID().toString(), "Idemp Debit").id!!
        transactionService.addCredit(partnerId, BigDecimal("200.00"), "Seed", "idem-debit-seed")

        transactionService.consumeCredit(partnerId, BigDecimal("50.00"), "First", "idem-debit-1")

        assertThrows<DuplicateTransactionException> {
            transactionService.consumeCredit(partnerId, BigDecimal("50.00"), "Duplicate", "idem-debit-1")
        }

        assertEquals(
            0,
            BigDecimal("150.00").compareTo(queryBalance(partnerId)),
            "Balance should be 150 — second debit must not have been applied",
        )
        assertEquals(
            1,
            countTransactions("idem-debit-1"),
            "Only one transaction record should exist",
        )
    }

    @Test
    fun `concurrent requests with same idempotency key should process only once`() {
        val partnerId = partnerService.createPartner(UUID.randomUUID().toString(), "Idemp Concurrent").id!!
        val idempotencyKey = "idem-concurrent-${UUID.randomUUID()}"

        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val duplicateCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    transactionService.addCredit(partnerId, BigDecimal("100.00"), "Concurrent", idempotencyKey)
                    successCount.incrementAndGet()
                } catch (_: DuplicateTransactionException) {
                    duplicateCount.incrementAndGet()
                } catch (_: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val finalBalance = queryBalance(partnerId)
        val txCount = countTransactions(idempotencyKey)

        assertTrue(
            successCount.get() <= 1,
            "At most 1 thread should succeed, got ${successCount.get()}",
        )
        assertEquals(
            1,
            txCount,
            "Exactly one transaction record should exist",
        )
        assertTrue(
            finalBalance.compareTo(BigDecimal("100.00")) <= 0,
            "Balance should be at most 100, got $finalBalance",
        )
    }
}
