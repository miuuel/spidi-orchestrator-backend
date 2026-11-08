package com.spidi.orchestrator.api

import com.spidi.orchestrator.api.dto.BankNotificationRequest
import com.spidi.orchestrator.api.dto.UpdateRateRequest
import com.spidi.orchestrator.core.ExchangeRateService
import com.spidi.orchestrator.core.ReconciliationEngine
import com.spidi.orchestrator.core.ReconciliationResult
import com.spidi.orchestrator.core.WebhookDispatcher
import com.spidi.orchestrator.domain.BankNotification
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/simulator")
@Tag(name = "Simulator", description = "Herramientas de sandbox para pruebas de desarrollo e integración")
class SimulatorController(
    private val exchangeRateService: ExchangeRateService,
    private val reconciliationEngine: ReconciliationEngine,
    private val webhookDispatcher: WebhookDispatcher
) {

    @GetMapping("/rate")
    @Operation(summary = "Consultar tasa oficial actual", description = "Obtiene la tasa de cambio USD/VES configurada en el sistema (tasa tipo BCV).")
    fun getRate(): ResponseEntity<Map<String, Any>> {
        val rate = exchangeRateService.getUsdToVesRate()
        return ResponseEntity.ok(mapOf("usdToVes" to rate, "currency" to "VES"))
    }

    @PostMapping("/rate")
    @Operation(summary = "Actualizar tasa oficial USD/VES", description = "Modifica la tasa de cambio vigente y actualiza la caché de Redis.")
    fun updateRate(@Valid @RequestBody req: UpdateRateRequest): ResponseEntity<Map<String, Any>> {
        val updated = exchangeRateService.updateUsdToVesRate(req.newRate)
        return ResponseEntity.ok(mapOf("updatedRate" to updated, "message" to "Tasa actualizada correctamente"))
    }

    @PostMapping("/pago-movil/emit-transfer")
    @Operation(
        summary = "Simular transferencia bancaria entrante (Auto-conciliación)",
        description = "Simula que un usuario realizó un Pago Móvil desde su banco hacia la cuenta del comercio. Ejecuta el cruce instantáneo con la conciliación automática."
    )
    fun simulateBankTransfer(@Valid @RequestBody req: BankNotificationRequest): ResponseEntity<ReconciliationResult> {
        val notification = BankNotification(
            bankCode = req.bankCode,
            reference = req.reference,
            amount = req.amount,
            senderPhone = req.senderPhone,
            senderNationalId = req.senderNationalId
        )
        val result = reconciliationEngine.processBankNotification(notification)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/verify-signature")
    @Operation(
        summary = "Verificar firma de webhook HMAC SHA-256",
        description = "Herramienta para desarrolladores: permite validar si el código de verificación del comercio coincide con la firma enviada por Spidi."
    )
    fun verifySignature(
        @RequestParam payload: String,
        @RequestParam secret: String,
        @RequestParam signatureHeader: String
    ): ResponseEntity<Map<String, Any>> {
        val valid = webhookDispatcher.verifyHmacSignature(payload, secret, signatureHeader)
        return ResponseEntity.ok(mapOf("valid" to valid, "signatureHeader" to signatureHeader))
    }
}
