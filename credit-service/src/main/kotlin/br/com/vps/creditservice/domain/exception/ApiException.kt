package br.com.vps.creditservice.domain.exception

import org.springframework.http.HttpStatus
import java.util.UUID

abstract class ApiException(
    message: String,
    val httpStatus: HttpStatus,
) : RuntimeException(message)

class PartnerNotFoundException(
    partnerId: UUID,
) : ApiException("Partner not found: $partnerId", HttpStatus.NOT_FOUND)

class InsufficientBalanceException(
    partnerId: UUID,
) : ApiException("Insufficient balance for partner: $partnerId", HttpStatus.UNPROCESSABLE_ENTITY)

class DuplicateTransactionException(
    idempotencyKey: String,
) : ApiException("Transaction already processed with key: $idempotencyKey", HttpStatus.CONFLICT)

class TransactionNotFoundException(
    transactionId: UUID,
) : ApiException("Transaction not found: $transactionId", HttpStatus.NOT_FOUND)

class InvalidAmountException : ApiException("Amount must be greater than zero", HttpStatus.BAD_REQUEST)

class PartnerNotFoundByExternalIdException(
    externalId: String,
) : ApiException("Partner not found with externalId: $externalId", HttpStatus.NOT_FOUND)

class PartnerAlreadyExistsException(
    externalId: String,
) : ApiException("Partner already exists with externalId: $externalId", HttpStatus.CONFLICT)
