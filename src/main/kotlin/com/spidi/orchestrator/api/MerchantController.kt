package com.spidi.orchestrator.api

import com.spidi.orchestrator.api.dto.CreateMerchantRequest
import com.spidi.orchestrator.domain.Merchant
import com.spidi.orchestrator.domain.WebhookDelivery
import com.spidi.orchestrator.repository.MerchantRepository
import com.spidi.orchestrator.repository.WebhookDeliveryRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchants", description = "Gestión de comercios, credenciales de API y webhooks")
class MerchantController(
    private val merchantRepository: MerchantRepository,
    private val webhookDeliveryRepository: WebhookDeliveryRepository
) {

    @PostMapping
    @Operation(summary = "Registrar nuevo comercio", description = "Genera credenciales de producción/sandbox (`spidi_sec_...`), clave pública y secreto para firma de webhooks (`whsec_...`).")
    fun createMerchant(@Valid @RequestBody req: CreateMerchantRequest): ResponseEntity<Merchant> {
        val existing = merchantRepository.findByEmail(req.email)
        if (existing.isPresent) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(existing.get())
        }

        val merchant = Merchant(
            name = req.name,
            email = req.email,
            webhookUrl = req.webhookUrl
        )
        val saved = merchantRepository.save(merchant)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar perfil del comercio autenticado", description = "Obtiene los detalles del comercio a partir de la cabecera X-API-KEY.")
    fun getMyProfile(@RequestHeader("X-API-KEY") apiKey: String): ResponseEntity<Merchant> {
        val merchant = merchantRepository.findByApiKeySecret(apiKey)
            .orElseThrow { IllegalArgumentException("Clave API de comercio inválida") }
        return ResponseEntity.ok(merchant)
    }

    @GetMapping("/webhooks")
    @Operation(summary = "Auditoría de entregas de webhooks", description = "Historial de webhooks enviados al comercio, códigos de respuesta HTTP y firmas generadas.")
    fun getWebhookDeliveries(@RequestHeader("X-API-KEY") apiKey: String): ResponseEntity<List<WebhookDelivery>> {
        val merchant = merchantRepository.findByApiKeySecret(apiKey)
            .orElseThrow { IllegalArgumentException("Clave API de comercio inválida") }
        val deliveries = webhookDeliveryRepository.findByMerchantId(merchant.id)
        return ResponseEntity.ok(deliveries)
    }
}
