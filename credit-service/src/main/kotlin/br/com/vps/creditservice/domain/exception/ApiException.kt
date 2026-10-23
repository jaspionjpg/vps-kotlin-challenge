package br.com.vps.creditservice.domain.exception

import org.springframework.http.HttpStatus

abstract class ApiException(
    message: String,
    val httpStatus: HttpStatus
) : RuntimeException(message)

class PartnerNotFoundException(partnerId: String) :
    ApiException("Partner not found: $partnerId", HttpStatus.NOT_FOUND)

class InsufficientBalanceException(partnerId: String) :
    ApiException("Insufficient balance for partner: $partnerId", HttpStatus.UNPROCESSABLE_ENTITY)

class DuplicateTransactionException(idempotencyKey: String) :
    ApiException("Transaction already processed with key: $idempotencyKey", HttpStatus.CONFLICT)

class InvalidAmountException :
    ApiException("Amount must be greater than zero", HttpStatus.BAD_REQUEST)

class PartnerAlreadyExistsException(externalId: String) :
    ApiException("Partner already exists with externalId: $externalId", HttpStatus.CONFLICT)
