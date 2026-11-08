package com.spidi.orchestrator.api

import com.spidi.orchestrator.api.dto.ConfirmPaymentRequest
import com.spidi.orchestrator.api.dto.CreatePaymentRequest
import com.spidi.orchestrator.api.dto.PaymentResponse
import com.spidi.orchestrator.core.CreatePaymentCommand
import com.spidi.orchestrator.core.PaymentOrchestrator
import com.spidi.orchestrator.repository.MerchantRepository
import com.spidi.orchestrator.repository.PaymentRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Operaciones principales de creación, consulta y confirmación de cobros")
class PaymentController(
    private val paymentOrchestrator: PaymentOrchestrator,
    private val paymentRepository: PaymentRepository,
    private val merchantRepository: MerchantRepository
) {

    @PostMapping
    @Operation(
        summary = "Iniciar un nuevo cobro unificado",
        description = "Crea un intento de pago en el riel seleccionado (Pago Móvil C2P, P2C, Cripto USDT o Tarjeta). Soporta control de idempotencia mediante la cabecera 'Idempotency-Key'."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Cobro iniciado exitosamente"),
        ApiResponse(responseCode = "400", description = "Datos de solicitud inválidos"),
        ApiResponse(responseCode = "401", description = "Clave API inválida"),
        ApiResponse(responseCode = "409", description = "Conflicto por concurrencia o idempotencia en proceso")
    )
    fun createPayment(
        @Parameter(description = "Clave Secreta de API del Comercio (ej. spidi_sec_...)", required = true)
        @RequestHeader("X-API-KEY") apiKey: String,

        @Parameter(description = "Clave única de idempotencia para prevenir duplicados", required = false)
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,

        @Valid @RequestBody req: CreatePaymentRequest
    ): ResponseEntity<PaymentResponse> {
        val merchant = merchantRepository.findByApiKeySecret(apiKey)
            .orElseThrow { IllegalArgumentException("Clave API de comercio inválida") }

        val cmd = CreatePaymentCommand(
            merchantId = merchant.id,
            amount = req.amount,
            currency = req.currency,
            rail = req.rail,
            description = req.description,
            customerPhone = req.customerPhone,
            customerNationalId = req.customerNationalId,
            customerName = req.customerName,
            customerBankCode = req.customerBankCode,
            idempotencyKey = idempotencyKey,
            metadata = req.metadata
        )

        val result = paymentOrchestrator.createPayment(cmd)
        val status = if (result.cachedFromIdempotency) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(PaymentResponse.fromDomain(result.payment, result.instructions))
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el estado de un pago", description = "Devuelve los detalles actualizados del pago, estado de conciliación y referencias bancarias.")
    fun getPayment(
        @Parameter(description = "ID único del pago", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<PaymentResponse> {
        val payment = paymentRepository.findById(id)
            .orElseThrow { NoSuchElementException("Pago no encontrado: $id") }
        return ResponseEntity.ok(PaymentResponse.fromDomain(payment))
    }

    @PostMapping("/{id}/confirm")
    @Operation(
        summary = "Confirmar pago (OTP, Referencia bancaria, Cripto o Tarjeta)",
        description = "Procesa la confirmación del pago según el riel: debita C2P con OTP, asocia referencia en P2C, valida hash en Cripto o procesa tarjeta."
    )
    fun confirmPayment(
        @Parameter(description = "ID del pago a confirmar", required = true)
        @PathVariable id: UUID,

        @RequestBody req: ConfirmPaymentRequest
    ): ResponseEntity<PaymentResponse> {
        val params = mutableMapOf<String, Any>()
        req.otp?.let { params["otp"] = it }
        req.reference?.let { params["reference"] = it }
        req.txHash?.let { params["txHash"] = it }
        req.cardNumber?.let { params["cardNumber"] = it }
        req.expMonth?.let { params["expMonth"] = it }
        req.expYear?.let { params["expYear"] = it }
        req.cvv?.let { params["cvv"] = it }
        req.bankCode?.let { params["bankCode"] = it }

        val updatedPayment = paymentOrchestrator.confirmPayment(id, params)
        return ResponseEntity.ok(PaymentResponse.fromDomain(updatedPayment))
    }

    @GetMapping
    @Operation(summary = "Listar pagos del comercio", description = "Devuelve el historial de cobros realizados por el comercio autenticado.")
    fun listPayments(
        @RequestHeader("X-API-KEY") apiKey: String
    ): ResponseEntity<List<PaymentResponse>> {
        val merchant = merchantRepository.findByApiKeySecret(apiKey)
            .orElseThrow { IllegalArgumentException("Clave API de comercio inválida") }

        val payments = paymentRepository.findByMerchantId(merchant.id)
        return ResponseEntity.ok(payments.map { PaymentResponse.fromDomain(it) })
    }
}
