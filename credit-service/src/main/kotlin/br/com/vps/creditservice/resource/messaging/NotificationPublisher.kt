package br.com.vps.creditservice.resource.messaging

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import tools.jackson.databind.ObjectMapper

@Component
class NotificationPublisher(
    private val snsClient: SnsClient,
    private val objectMapper: ObjectMapper,
    @Value("\${app.notification.topic-arn}") private val topicArn: String,
) {
    private val log = LoggerFactory.getLogger(NotificationPublisher::class.java)

    fun publish(event: TransactionEvent) {
        try {
            val message = objectMapper.writeValueAsString(event)
            val request =
                PublishRequest
                    .builder()
                    .topicArn(topicArn)
                    .message(message)
                    .messageAttributes(buildAttributes(event))
                    .build()

            val response = snsClient.publish(request)
            log.info("Published transaction event {} to SNS, messageId={}", event.transactionId, response.messageId())
        } catch (e: Exception) {
            log.error("Failed to publish transaction event {} to SNS", event.transactionId, e)
        }
    }

    private fun buildAttributes(event: TransactionEvent): Map<String, MessageAttributeValue> =
        mapOf(
            "eventType" to
                MessageAttributeValue
                    .builder()
                    .dataType("String")
                    .stringValue("TRANSACTION_${event.type.name}")
                    .build(),
        )
}
