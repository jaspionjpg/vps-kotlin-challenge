package br.com.vps.creditservice.resource.messaging

import br.com.vps.creditservice.resource.model.enums.ETransactionStatus
import br.com.vps.creditservice.resource.model.enums.ETransactionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.model.PublishResponse
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

class NotificationPublisherTest {
    private val snsClient: SnsClient = mock()
    private val objectMapper = ObjectMapper()
    private val topicArn = "arn:aws:sns:us-east-1:000000000000:credit-transactions"
    private lateinit var publisher: NotificationPublisher

    @BeforeEach
    fun setup() {
        publisher = NotificationPublisher(snsClient, objectMapper, topicArn)
    }

    private fun createEvent(type: ETransactionType = ETransactionType.CREDIT) =
        TransactionEvent(
            transactionId = UUID.randomUUID(),
            partnerId = UUID.randomUUID(),
            type = type,
            amount = BigDecimal("100.00"),
            status = ETransactionStatus.COMPLETED,
            description = "Test event",
            timestamp = OffsetDateTime.now(),
        )

    @Test
    fun `publishWithRetry should send message to SNS with correct topic ARN`() {
        val event = createEvent()
        whenever(snsClient.publish(any<PublishRequest>())).thenReturn(
            PublishResponse.builder().messageId("msg-123").build(),
        )

        publisher.publishWithRetry(event)

        verify(snsClient).publish(argThat<PublishRequest> { topicArn() == this@NotificationPublisherTest.topicArn })
    }

    @Test
    fun `publishWithRetry should include TRANSACTION_CREDIT event type attribute for credit`() {
        val event = createEvent(ETransactionType.CREDIT)
        whenever(snsClient.publish(any<PublishRequest>())).thenReturn(
            PublishResponse.builder().messageId("msg-123").build(),
        )

        publisher.publishWithRetry(event)

        verify(snsClient).publish(
            argThat<PublishRequest> {
                messageAttributes()["eventType"]?.stringValue() == "TRANSACTION_CREDIT"
            },
        )
    }

    @Test
    fun `publishWithRetry should include TRANSACTION_DEBIT event type attribute for debit`() {
        val event = createEvent(ETransactionType.DEBIT)
        whenever(snsClient.publish(any<PublishRequest>())).thenReturn(
            PublishResponse.builder().messageId("msg-123").build(),
        )

        publisher.publishWithRetry(event)

        verify(snsClient).publish(
            argThat<PublishRequest> {
                messageAttributes()["eventType"]?.stringValue() == "TRANSACTION_DEBIT"
            },
        )
    }

    @Test
    fun `publishWithRetry should throw on SNS failure for retry mechanism`() {
        val event = createEvent()
        whenever(snsClient.publish(any<PublishRequest>())).thenThrow(RuntimeException("SNS unavailable"))

        org.junit.jupiter.api.assertThrows<RuntimeException> {
            publisher.publishWithRetry(event)
        }
    }

    @Test
    fun `recover should not throw`() {
        val event = createEvent()
        publisher.recover(RuntimeException("All retries exhausted"), event)
    }

    @Test
    fun `onTransactionEvent should delegate to publishWithRetry`() {
        val event = createEvent()
        whenever(snsClient.publish(any<PublishRequest>())).thenReturn(
            PublishResponse.builder().messageId("msg-123").build(),
        )

        publisher.onTransactionEvent(event)

        verify(snsClient).publish(any<PublishRequest>())
    }
}
