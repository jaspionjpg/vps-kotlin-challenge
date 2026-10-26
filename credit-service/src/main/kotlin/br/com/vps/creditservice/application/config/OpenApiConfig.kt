package br.com.vps.creditservice.application.config

import br.com.vps.creditservice.resource.model.enums.EHttpHeader
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Credit Service API")
                    .description("B2B partner credit management system — manage balances, process transactions, and track history.")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("VPS")
                            .email("contact@vps.com.br"),
                    ),
            ).components(
                Components().addSecuritySchemes(
                    "ApiKeyAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .`in`(SecurityScheme.In.HEADER)
                        .name(EHttpHeader.API_KEY.headerName),
                ),
            ).addSecurityItem(SecurityRequirement().addList("ApiKeyAuth"))
}
