package br.com.vps.creditservice.application.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

// NOTE: Current auth uses a single shared API key. In production, this should be replaced with
// per-partner credentials (e.g., OAuth2 client_credentials per partner) so each partner can only
// access their own resources, enabling proper tenant isolation at the auth layer.
@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${app.security.api-key}") private val apiKey: String,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun apiKeyAuthFilter(): ApiKeyAuthFilter = ApiKeyAuthFilter(apiKey, objectMapper)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
