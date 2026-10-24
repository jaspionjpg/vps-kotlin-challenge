package br.com.vps.creditservice.application.controller

import br.com.vps.creditservice.application.controller.request.CreatePartnerRequest
import br.com.vps.creditservice.application.controller.response.BalanceResponse
import br.com.vps.creditservice.application.controller.response.PartnerResponse
import br.com.vps.creditservice.domain.service.PartnerService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/partners")
@Tag(name = "Partners", description = "Partner management endpoints")
class PartnerController(
    private val partnerService: PartnerService,
) {
    @PostMapping
    @Operation(summary = "Create a new partner")
    fun createPartner(
        @Valid @RequestBody request: CreatePartnerRequest,
    ): ResponseEntity<PartnerResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(PartnerResponse.from(partnerService.createPartner(request.externalId, request.name)))

    @GetMapping("/{partnerId}")
    @Operation(summary = "Get partner details")
    fun getPartner(
        @PathVariable partnerId: UUID,
    ): ResponseEntity<PartnerResponse> = ResponseEntity.ok(PartnerResponse.from(partnerService.findById(partnerId)))

    @GetMapping("/{partnerId}/balance")
    @Operation(summary = "Get partner credit balance")
    fun getBalance(
        @PathVariable partnerId: UUID,
    ): ResponseEntity<BalanceResponse> = ResponseEntity.ok(BalanceResponse(partnerId, partnerService.getBalance(partnerId)))
}
