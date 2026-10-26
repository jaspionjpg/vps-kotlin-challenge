package br.com.vps.creditservice.integration

import br.com.vps.creditservice.domain.service.BalanceCacheService
import br.com.vps.creditservice.domain.service.PartnerService
import br.com.vps.creditservice.domain.service.TransactionService
import br.com.vps.creditservice.resource.messaging.NotificationPublisher
import br.com.vps.creditservice.resource.model.enums.ETransactionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
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
class ConcurrencyTest {
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

    @Test
    fun `concurrent credits should produce correct final balance`() {
        val partnerId = partnerService.createPartner(UUID.randomUUID().toString(), "Credit Test").id!!

        val threadCount = 20
        val creditAmount = BigDecimal("10.00")
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) { i ->
            executor.submit {
                try {
                    transactionService.addCredit(partnerId, creditAmount, "Concurrent credit $i", "cc-$partnerId-$i")
                    successCount.incrementAndGet()
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val finalBalance = queryBalance(partnerId)
        val expectedBalance = creditAmount.multiply(BigDecimal(successCount.get()))
        assertEquals(
            0,
            expectedBalance.compareTo(finalBalance),
            "Expected $expectedBalance but got $finalBalance after ${successCount.get()} successful credits",
        )
    }

    @Test
    fun `concurrent debits should never result in negative balance`() {
        val partnerId = partnerService.createPartner(UUID.randomUUID().toString(), "Debit Test").id!!
        transactionService.addCredit(partnerId, BigDecimal("100.00"), "Seed", "seed-$partnerId")

        val threadCount = 20
        val debitAmount = BigDecimal("10.00")
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) { i ->
            executor.submit {
                try {
                    val result = transactionService.consumeCredit(partnerId, debitAmount, "Debit $i", "cd-$partnerId-$i")
                    if (result.status == ETransactionStatus.COMPLETED) successCount.incrementAndGet()
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val finalBalance = queryBalance(partnerId)

        assert(finalBalance >= BigDecimal.ZERO) { "Balance went negative: $finalBalance" }
        assertEquals(10, successCount.get(), "Expected 10 successful debits but got ${successCount.get()}")
        assertEquals(0, BigDecimal.ZERO.compareTo(finalBalance), "Expected balance 0 but got $finalBalance")
    }

    @Test
    fun `mixed concurrent credits and debits should produce correct balance`() {
        val partnerId = partnerService.createPartner(UUID.randomUUID().toString(), "Mixed Test").id!!
        transactionService.addCredit(partnerId, BigDecimal("500.00"), "Seed", "mixed-seed-$partnerId")

        val threadCount = 10
        val amount = BigDecimal("20.00")
        val executor = Executors.newFixedThreadPool(threadCount * 2)
        val latch = CountDownLatch(threadCount * 2)
        val creditSuccess = AtomicInteger(0)
        val debitSuccess = AtomicInteger(0)

        repeat(threadCount) { i ->
            executor.submit {
                try {
                    val r = transactionService.addCredit(partnerId, amount, "Credit $i", "mc-$partnerId-$i")
                    if (r.status == ETransactionStatus.COMPLETED) creditSuccess.incrementAndGet()
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }
        }
        repeat(threadCount) { i ->
            executor.submit {
                try {
                    val r = transactionService.consumeCredit(partnerId, amount, "Debit $i", "md-$partnerId-$i")
                    if (r.status == ETransactionStatus.COMPLETED) debitSuccess.incrementAndGet()
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val finalBalance = queryBalance(partnerId)
        val expectedBalance =
            BigDecimal("500.00")
                .add(amount.multiply(BigDecimal(creditSuccess.get())))
                .subtract(amount.multiply(BigDecimal(debitSuccess.get())))

        assertEquals(
            0,
            expectedBalance.compareTo(finalBalance),
            "Expected $expectedBalance but got $finalBalance (credits: ${creditSuccess.get()}, debits: ${debitSuccess.get()})",
        )
        assert(finalBalance >= BigDecimal.ZERO) { "Balance went negative: $finalBalance" }
    }
}
