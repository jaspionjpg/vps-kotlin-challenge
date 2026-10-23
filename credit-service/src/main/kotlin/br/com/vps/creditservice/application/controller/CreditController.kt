package br.com.vps.creditservice.application.controller

import br.com.vps.creditservice.application.controller.request.CreditRequest
import br.com.vps.creditservice.application.controller.request.DebitRequest
import br.com.vps.creditservice.application.controller.response.TransactionResponse
import br.com.vps.creditservice.domain.service.TransactionService
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

@RestController
@RequestMapping("/partners/{partnerId}/transactions")
@Tag(name = "Transactions", description = "Credit and debit transaction endpoints")
class CreditController(
    private val transactionService: TransactionService
) {

    @PostMapping("/credit")
    @Operation(summary = "Add credits to a partner")
    fun addCredit(
        @PathVariable partnerId: String,
        @RequestHeader("Idempotency-Key") @Parameter(description = "Unique key to prevent duplicate transactions") idempotencyKey: String,
        @Valid @RequestBody request: CreditRequest
    ): ResponseEntity<TransactionResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                TransactionResponse.from(
                    transactionService.addCredit(partnerId, request.amount, request.description, idempotencyKey)
                )
            )

    @PostMapping("/debit")
    @Operation(summary = "Consume credits from a partner")
    fun consumeCredit(
        @PathVariable partnerId: String,
        @RequestHeader("Idempotency-Key") @Parameter(description = "Unique key to prevent duplicate transactions") idempotencyKey: String,
        @Valid @RequestBody request: DebitRequest
    ): ResponseEntity<TransactionResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                TransactionResponse.from(
                    transactionService.consumeCredit(partnerId, request.amount, request.description, idempotencyKey)
                )
            )

    @GetMapping
    @Operation(summary = "Get transaction history for a partner")
    fun getTransactionHistory(
        @PathVariable partnerId: String,
        @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable
    ): ResponseEntity<Page<TransactionResponse>> =
        ResponseEntity.ok(
            transactionService.getTransactionHistory(partnerId, pageable).map { TransactionResponse.from(it) }
        )

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction details")
    fun getTransaction(
        @PathVariable partnerId: String,
        @PathVariable transactionId: String
    ): ResponseEntity<TransactionResponse> =
        ResponseEntity.ok(TransactionResponse.from(transactionService.findById(transactionId)))
}
