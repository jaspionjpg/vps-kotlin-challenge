package br.com.vps.creditservice.application.controller

import br.com.vps.creditservice.application.controller.response.GenericErrorResponse
import br.com.vps.creditservice.domain.exception.ApiException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
class RestExceptionHandler {
    private val log = LoggerFactory.getLogger(RestExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(
        ex: ApiException,
        request: WebRequest,
    ): ResponseEntity<GenericErrorResponse> {
        val response =
            GenericErrorResponse(
                status = ex.httpStatus.value(),
                error = ex::class.java.simpleName,
                message = ex.message,
                path = extractPath(request),
            )
        return ResponseEntity(response, ex.httpStatus)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: WebRequest,
    ): ResponseEntity<GenericErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        val response =
            GenericErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "ValidationError",
                message = message,
                path = extractPath(request),
            )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(
        ex: MissingRequestHeaderException,
        request: WebRequest,
    ): ResponseEntity<GenericErrorResponse> {
        val response =
            GenericErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "MissingHeader",
                message = ex.message,
                path = extractPath(request),
            )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(
        ex: DataIntegrityViolationException,
        request: WebRequest,
    ): ResponseEntity<GenericErrorResponse> {
        log.warn("Data integrity violation: {}", ex.message)
        val message =
            if (ex.message?.contains("idempotency_key") == true) {
                "Transaction already processed with the provided idempotency key"
            } else {
                "A conflict occurred while processing the request"
            }
        val response =
            GenericErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = "Conflict",
                message = message,
                path = extractPath(request),
            )
        return ResponseEntity(response, HttpStatus.CONFLICT)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        request: WebRequest,
    ): ResponseEntity<GenericErrorResponse> {
        log.error("Unexpected error", ex)
        val response =
            GenericErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "InternalServerError",
                message = "An unexpected error occurred",
                path = extractPath(request),
            )
        return ResponseEntity(response, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    private fun extractPath(request: WebRequest): String = (request as? ServletWebRequest)?.request?.requestURI ?: ""
}
