package br.com.vps.creditservice.domain.exception

import org.springframework.http.HttpStatus
import java.util.UUID

abstract class ApiException(
    message: String,
    val httpStatus: HttpStatus,
) : RuntimeException(message)

class PartnerNotFoundException(
    partnerId: UUID,
) : ApiException("Partner not found", HttpStatus.NOT_FOUND)

class InsufficientBalanceException(
    partnerId: UUID,
) : ApiException("Insufficient balance for partner", HttpStatus.UNPROCESSABLE_ENTITY)

class DuplicateTransactionException(
    idempotencyKey: String,
) : ApiException("Transaction already processed with the provided idempotency key", HttpStatus.CONFLICT)

class TransactionNotFoundException(
    transactionId: UUID,
) : ApiException("Transaction not found", HttpStatus.NOT_FOUND)

class InvalidAmountException : ApiException("Amount must be greater than zero", HttpStatus.BAD_REQUEST)

class InvalidIdempotencyKeyException(
    reason: String,
) : ApiException(reason, HttpStatus.BAD_REQUEST)

class PartnerNotFoundByExternalIdException(
    externalId: String,
) : ApiException("Partner not found with the provided external ID", HttpStatus.NOT_FOUND)

class PartnerAlreadyExistsException(
    externalId: String,
) : ApiException("Partner already exists with the provided external ID", HttpStatus.CONFLICT)
