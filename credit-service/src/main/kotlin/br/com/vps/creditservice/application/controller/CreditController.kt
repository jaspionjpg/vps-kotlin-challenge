package br.com.vps.creditservice.application.controller

import br.com.vps.creditservice.application.controller.request.CreditRequest
import br.com.vps.creditservice.application.controller.request.DebitRequest
import br.com.vps.creditservice.application.controller.response.TransactionResponse
import br.com.vps.creditservice.domain.service.TransactionService
import br.com.vps.creditservice.resource.model.TransactionEntity
import br.com.vps.creditservice.resource.model.enums.EHttpHeader
import br.com.vps.creditservice.resource.model.enums.ETransactionStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/partners/{partnerId}/transactions")
@Tag(name = "Transactions", description = "Credit and debit transaction endpoints")
class CreditController(
    private val transactionService: TransactionService,
) {
    @PostMapping("/credit")
    @Operation(summary = "Add credits to a partner")
    fun addCredit(
        @PathVariable partnerId: UUID,
        @RequestHeader(EHttpHeader.IDEMPOTENCY_KEY_VALUE)
        @Parameter(description = "Unique key to prevent duplicate transactions")
        idempotencyKey: String,
        @Valid @RequestBody request: CreditRequest,
    ): ResponseEntity<TransactionResponse> =
        transactionService.addCredit(partnerId, request.amount, request.description, idempotencyKey).let {
            ResponseEntity.status(it.httpStatus()).body(TransactionResponse.from(it))
        }

    @PostMapping("/debit")
    @Operation(summary = "Consume credits from a partner")
    fun consumeCredit(
        @PathVariable partnerId: UUID,
        @RequestHeader(EHttpHeader.IDEMPOTENCY_KEY_VALUE)
        @Parameter(description = "Unique key to prevent duplicate transactions")
        idempotencyKey: String,
        @Valid @RequestBody request: DebitRequest,
    ): ResponseEntity<TransactionResponse> =
        transactionService.consumeCredit(partnerId, request.amount, request.description, idempotencyKey).let {
            ResponseEntity.status(it.httpStatus()).body(TransactionResponse.from(it))
        }

    @GetMapping
    @Operation(summary = "Get transaction history for a partner")
    fun getTransactionHistory(
        @PathVariable partnerId: UUID,
        @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable,
    ): ResponseEntity<Page<TransactionResponse>> =
        ResponseEntity.ok(
            transactionService.getTransactionHistory(partnerId, pageable).map { TransactionResponse.from(it) },
        )

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction details")
    fun getTransaction(
        @PathVariable partnerId: UUID,
        @PathVariable transactionId: UUID,
    ): ResponseEntity<TransactionResponse> =
        ResponseEntity.ok(TransactionResponse.from(transactionService.findByIdForPartner(transactionId, partnerId)))

    private fun TransactionEntity.httpStatus(): HttpStatus =
        if (status == ETransactionStatus.FAILED) HttpStatus.UNPROCESSABLE_ENTITY else HttpStatus.CREATED
}
