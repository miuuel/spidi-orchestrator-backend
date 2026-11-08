package com.spidi.orchestrator.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "ApiKeyAuth"
        return OpenAPI()
            .info(
                Info()
                    .title("Spidi Payment Orchestration Engine API")
                    .description(
                        """
                        ## Spidi MVP - Orquestador de Pagos y Cobranzas Digitales
                        Plataforma de orquestación de pagos multi-riel inspirada en Spidi (Venezuela).
                        
                        ### Rieles de Pago Soportados:
                        - **Pago Móvil C2P**: Débito directo con token dinámico OTP bancario.
                        - **Pago Móvil P2C**: Conciliación en tiempo real por cruce de referencia bancaria (sin captures de pantalla).
                        - **Cripto USDT**: Cobros en stablecoin con tasa de cambio oficial o cripto.
                        - **Tarjetas de Débito/Crédito**: Procesamiento unificado y tokenizado.
                        
                        ### Características de Nivel Fintech:
                        - **Idempotencia distribuida** vía Redis (`Idempotency-Key`).
                        - **Webhooks con firma HMAC SHA-256** (`X-Spidi-Signature`).
                        - **Conversión de Divisas** con tasa oficial configurable (USD / VES / USDT).
                        """.trimIndent()
                    )
                    .version("1.0.0-MVP")
                    .contact(
                        Contact()
                            .name("Equipo de Ingeniería Spidi")
                            .email("soporte@spidi.local")
                    )
                    .license(License().name("Proprietary").url("https://spidi.lat"))
            )
            .addServersItem(Server().url("http://localhost:8080").description("Servidor de Desarrollo Local"))
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .components(
                Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .name("X-API-KEY")
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.HEADER)
                            .description("Clave Secreta de API del Comercio (ej. spidi_sec_...)")
                    )
            )
    }
}
