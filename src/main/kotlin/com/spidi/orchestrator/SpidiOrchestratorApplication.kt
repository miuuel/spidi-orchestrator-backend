package com.spidi.orchestrator

import com.spidi.orchestrator.domain.Merchant
import com.spidi.orchestrator.repository.MerchantRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class SpidiOrchestratorApplication {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun initDemoData(merchantRepository: MerchantRepository): CommandLineRunner {
        return CommandLineRunner {
            val demoEmail = "demo@spidi.lat"
            if (merchantRepository.findByEmail(demoEmail).isEmpty) {
                val demoMerchant = Merchant(
                    name = "Demo Store Venezuela C.A.",
                    email = demoEmail,
                    apiKeyPublic = "spidi_pub_demo123456789012345",
                    apiKeySecret = "spidi_sec_demo_secret_key_99999",
                    webhookSecret = "whsec_demo_signature_secret_888",
                    webhookUrl = "http://localhost:8080/api/v1/simulator/webhook-test",
                    active = true
                )
                merchantRepository.save(demoMerchant)
                log.info("==========================================================================")
                log.info(">> SPIDI ORCHESTRATOR INICIALIZADO CON ÉXITO")
                log.info(">> Comercio Demo Registrado:")
                log.info("   - API Key Secreta (X-API-KEY): spidi_sec_demo_secret_key_99999")
                log.info("   - Webhook Secret:              whsec_demo_signature_secret_888")
                log.info(">> Swagger UI interactivo:        http://localhost:8080/swagger-ui.html")
                log.info(">> OpenAPI JSON:                  http://localhost:8080/v3/api-docs")
                log.info("==========================================================================")
            }
        }
    }
}

fun main(args: Array<String>) {
    runApplication<SpidiOrchestratorApplication>(*args)
}
