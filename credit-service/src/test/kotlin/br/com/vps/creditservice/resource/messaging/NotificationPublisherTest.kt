package br.com.vps.creditservice.resource.messaging

import br.com.vps.creditservice.resource.model.TransactionStatus
import br.com.vps.creditservice.resource.model.TransactionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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

    private fun createEvent(type: TransactionType = TransactionType.CREDIT) =
        TransactionEvent(
            transactionId = UUID.randomUUID(),
            partnerId = UUID.randomUUID(),
            type = type,
            amount = BigDecimal("100.00"),
            status = TransactionStatus.COMPLETED,
            description = "Test event",
            timestamp = OffsetDateTime.now(),
        )

    @Test
    fun `publish should send message to SNS with correct topic ARN`() {
        val event = createEvent()
        whenever(snsClient.publish(any<PublishRequest>())).thenReturn(
            PublishResponse.builder().messageId("msg-123").build(),
        )

        publisher.publish(event)

        verify(snsClient).publish(argThat<PublishRequest> { topicArn() == this@NotificationPublisherTest.topicArn })
    }

    @Test
    fun `publish should include TRANSACTION_CREDIT event type attribute for credit`() {
        val event = createEvent(TransactionType.CREDIT)
        whenever(snsClient.publish(any<PublishRequest>())).thenReturn(
            PublishResponse.builder().messageId("msg-123").build(),
        )

        publisher.publish(event)

        verify(snsClient).publish(
            argThat<PublishRequest> {
                messageAttributes()["eventType"]?.stringValue() == "TRANSACTION_CREDIT"
            },
        )
    }

    @Test
    fun `publish should include TRANSACTION_DEBIT event type attribute for debit`() {
        val event = createEvent(TransactionType.DEBIT)
        whenever(snsClient.publish(any<PublishRequest>())).thenReturn(
            PublishResponse.builder().messageId("msg-123").build(),
        )

        publisher.publish(event)

        verify(snsClient).publish(
            argThat<PublishRequest> {
                messageAttributes()["eventType"]?.stringValue() == "TRANSACTION_DEBIT"
            },
        )
    }

    @Test
    fun `publish should not throw when SNS fails`() {
        val event = createEvent()
        whenever(snsClient.publish(any<PublishRequest>())).thenThrow(RuntimeException("SNS unavailable"))

        publisher.publish(event)

        verify(snsClient).publish(any<PublishRequest>())
    }

    @Test
    fun `publish should not throw when serialization fails`() {
        val brokenMapper: ObjectMapper = mock()
        whenever(brokenMapper.writeValueAsString(any())).thenThrow(RuntimeException("Serialization error"))
        val brokenPublisher = NotificationPublisher(snsClient, brokenMapper, topicArn)

        brokenPublisher.publish(createEvent())

        verify(snsClient, never()).publish(any<PublishRequest>())
    }
}
