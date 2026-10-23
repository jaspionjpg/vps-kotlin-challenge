package br.com.vps.creditservice.application.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Credit Service API")
                .description("B2B partner credit management system — manage balances, process transactions, and track history.")
                .version("1.0.0")
                .contact(
                    Contact()
                        .name("VPS")
                        .email("contact@vps.com.br")
                )
        )
}
