package br.com.vps.creditservice.application.controller

import br.com.vps.creditservice.domain.service.BalanceCacheService
import br.com.vps.creditservice.domain.service.PartnerService
import br.com.vps.creditservice.domain.service.TransactionService
import br.com.vps.creditservice.resource.messaging.NotificationPublisher
import br.com.vps.creditservice.resource.model.PartnerEntity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.services.sns.SnsClient
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "spring.autoconfigure.exclude=" +
            "io.awspring.cloud.autoconfigure.sns.SnsAutoConfiguration," +
            "io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration," +
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration," +
            "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration",
        "app.security.api-key=test-secret-key",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
@AutoConfigureMockMvc
@Testcontainers
class SecurityTest {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:15.3")
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var partnerService: PartnerService

    @MockitoBean
    private lateinit var transactionService: TransactionService

    @MockitoBean
    private lateinit var snsClient: SnsClient

    @MockitoBean
    private lateinit var balanceCacheService: BalanceCacheService

    @MockitoBean
    private lateinit var notificationPublisher: NotificationPublisher

    @MockitoBean
    private lateinit var redisTemplate: org.springframework.data.redis.core.RedisTemplate<String, String>

    private val partnerId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `request without API key should return 401`() {
        mockMvc
            .perform(get("/partners/$partnerId").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("Invalid or missing API key"))
    }

    @Test
    fun `request with wrong API key should return 401`() {
        mockMvc
            .perform(
                get("/partners/$partnerId")
                    .header("X-API-Key", "wrong-key")
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Unauthorized"))
    }

    @Test
    fun `request with valid API key should pass authentication`() {
        val partner = PartnerEntity(externalId = "ext-1", name = "Acme Corp")
        whenever(partnerService.findById(partnerId)).thenReturn(partner)

        mockMvc
            .perform(
                get("/partners/$partnerId")
                    .header("X-API-Key", "test-secret-key")
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.externalId").value("ext-1"))
    }

    @Test
    fun `POST without API key should return 401`() {
        mockMvc
            .perform(
                post("/partners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalId": "ext-1", "name": "Acme"}"""),
            ).andExpect(status().isUnauthorized)
    }
}
