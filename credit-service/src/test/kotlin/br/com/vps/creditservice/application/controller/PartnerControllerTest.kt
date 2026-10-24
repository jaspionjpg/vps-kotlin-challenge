package br.com.vps.creditservice.application.controller

import br.com.vps.creditservice.domain.exception.PartnerAlreadyExistsException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.domain.service.PartnerService
import br.com.vps.creditservice.resource.model.PartnerEntity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(PartnerController::class)
class PartnerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var partnerService: PartnerService

    private val partnerId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `POST partners should create and return 201`() {
        val partner = PartnerEntity(externalId = "ext-1", name = "Acme Corp")
        whenever(partnerService.createPartner("ext-1", "Acme Corp")).thenReturn(partner)

        mockMvc.perform(
            post("/partners")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalId": "ext-1", "name": "Acme Corp"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.externalId").value("ext-1"))
            .andExpect(jsonPath("$.name").value("Acme Corp"))
            .andExpect(jsonPath("$.balance").value(0))
    }

    @Test
    fun `POST partners should return 409 when externalId already exists`() {
        whenever(partnerService.createPartner(any(), any())).thenThrow(PartnerAlreadyExistsException("ext-1"))

        mockMvc.perform(
            post("/partners")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalId": "ext-1", "name": "Duplicate"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("PartnerAlreadyExistsException"))
    }

    @Test
    fun `POST partners should return 400 when name is blank`() {
        mockMvc.perform(
            post("/partners")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalId": "ext-1", "name": ""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("ValidationError"))
    }

    @Test
    fun `GET partners by id should return partner`() {
        val partner = PartnerEntity(externalId = "ext-1", name = "Acme Corp", balance = BigDecimal("500.00"))
        whenever(partnerService.findById(partnerId)).thenReturn(partner)

        mockMvc.perform(get("/partners/$partnerId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(500.00))
    }

    @Test
    fun `GET partners by id should return 404 when not found`() {
        val missingId = UUID.fromString("00000000-0000-0000-0000-000000000099")
        whenever(partnerService.findById(missingId)).thenThrow(PartnerNotFoundException(missingId))

        mockMvc.perform(get("/partners/$missingId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("PartnerNotFoundException"))
    }

    @Test
    fun `GET partners balance should return balance`() {
        whenever(partnerService.getBalance(partnerId)).thenReturn(BigDecimal("1250.50"))

        mockMvc.perform(get("/partners/$partnerId/balance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.partnerId").value(partnerId.toString()))
            .andExpect(jsonPath("$.balance").value(1250.50))
    }
}
