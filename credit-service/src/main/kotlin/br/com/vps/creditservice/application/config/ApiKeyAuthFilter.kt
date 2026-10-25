package br.com.vps.creditservice.application.config

import br.com.vps.creditservice.application.controller.response.GenericErrorResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.OffsetDateTime

class ApiKeyAuthFilter(
    private val apiKey: String,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestApiKey = request.getHeader(API_KEY_HEADER)
        if (requestApiKey == null ||
            !MessageDigest.isEqual(requestApiKey.toByteArray(), apiKey.toByteArray())
        ) {
            val errorResponse =
                GenericErrorResponse(
                    status = HttpStatus.UNAUTHORIZED.value(),
                    error = "Unauthorized",
                    message = "Invalid or missing API key",
                    path = request.requestURI,
                    timestamp = OffsetDateTime.now(),
                )
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(objectMapper.writeValueAsString(errorResponse))
            return
        }

        SecurityContextHolder.getContext().authentication = ApiKeyAuthentication()
        filterChain.doFilter(request, response)
    }
}

class ApiKeyAuthentication : org.springframework.security.authentication.AbstractAuthenticationToken(emptyList()) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = "[PROTECTED]"

    override fun getPrincipal(): Any = "api-key-user"
}
