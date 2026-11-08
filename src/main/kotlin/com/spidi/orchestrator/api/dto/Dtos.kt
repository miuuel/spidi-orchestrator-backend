package com.spidi.orchestrator.api.dto

import com.spidi.orchestrator.domain.Currency
import com.spidi.orchestrator.domain.Payment
import com.spidi.orchestrator.domain.PaymentRail
import com.spidi.orchestrator.domain.PaymentStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Schema(description = "Solicitud de creación de cobro unificado")
data class CreatePaymentRequest(
    @field:NotNull(message = "El monto es obligatorio")
    @field:DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    @Schema(description = "Monto de la transacción", example = "10.00")
    val amount: BigDecimal,

    @Schema(description = "Moneda del cobro (USD, VES, USDT)", example = "USD")
    val currency: Currency = Currency.USD,

    @field:NotNull(message = "El riel de pago es obligatorio")
    @Schema(description = "Riel de pago seleccionado", example = "PAGOMOVIL_C2P")
    val rail: PaymentRail,

    @Schema(description = "Descripción del cobro", example = "Suscripción Premium Mensual")
    val description: String? = null,

    @Schema(description = "Número telefónico del pagador (formato 04141234567)", example = "04141234567")
    val customerPhone: String? = null,

    @Schema(description = "Cédula o RIF del pagador (ej. V12345678 o J123456789)", example = "V12345678")
    val customerNationalId: String? = null,

    @Schema(description = "Nombre o razón social del pagador", example = "Juan Pérez")
    val customerName: String? = null,

    @Schema(description = "Código del banco emisor del cliente (ej. 0102 Venezuela, 0105 Mercantil, 0134 Banesco)", example = "0102")
    val customerBankCode: String? = null,

    @Schema(description = "Metadatos adicionales en formato clave-valor", example = "{\"orderId\": \"ORD-98721\"}")
    val metadata: Map<String, Any>? = null
)

@Schema(description = "Solicitud de confirmación de pago")
data class ConfirmPaymentRequest(
    @Schema(description = "Clave dinámica OTP para cobros Pago Móvil C2P", example = "849201")
    val otp: String? = null,

    @Schema(description = "Número de referencia bancaria para Pago Móvil P2C", example = "98273615")
    val reference: String? = null,

    @Schema(description = "Hash de transacción blockchain para Cripto USDT", example = "0x89fba7291a1829e018274182749102")
    val txHash: String? = null,

    @Schema(description = "Número de tarjeta para cobros con tarjeta", example = "4532000000000000")
    val cardNumber: String? = null,

    @Schema(description = "Mes de expiración (1-12)", example = "12")
    val expMonth: Int? = null,

    @Schema(description = "Año de expiración (ej. 2028)", example = "2028")
    val expYear: Int? = null,

    @Schema(description = "Código CVV", example = "123")
    val cvv: String? = null,

    @Schema(description = "Código de banco emisor", example = "0102")
    val bankCode: String? = null
)

@Schema(description = "Respuesta detallada de un pago")
data class PaymentResponse(
    val id: UUID,
    val merchantId: UUID,
    val amount: BigDecimal,
    val currency: Currency,
    val amountVes: BigDecimal,
    val exchangeRate: BigDecimal,
    val rail: PaymentRail,
    val status: PaymentStatus,
    val description: String?,
    val customerPhone: String?,
    val customerNationalId: String?,
    val customerName: String?,
    val providerReference: String?,
    val instructions: Map<String, Any>?,
    val failureReason: String?,
    val checkoutUrl: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun fromDomain(payment: Payment, instructions: Map<String, Any>? = null, baseUrl: String = "http://localhost:8080"): PaymentResponse {
            return PaymentResponse(
                id = payment.id,
                merchantId = payment.merchantId,
                amount = payment.amount,
                currency = payment.currency,
                amountVes = payment.amountVes,
                exchangeRate = payment.exchangeRate,
                rail = payment.rail,
                status = payment.status,
                description = payment.description,
                customerPhone = payment.customerPhone,
                customerNationalId = payment.customerNationalId,
                customerName = payment.customerName,
                providerReference = payment.providerReference,
                instructions = instructions,
                failureReason = payment.failureReason,
                checkoutUrl = "$baseUrl/checkout/${payment.id}",
                createdAt = payment.createdAt,
                updatedAt = payment.updatedAt
            )
        }
    }
}

@Schema(description = "Notificación bancaria entrante para conciliación automática")
data class BankNotificationRequest(
    @field:NotBlank(message = "El código de banco es obligatorio")
    @Schema(description = "Código de banco receptor/emisor (ej. 0134, 0102)", example = "0134")
    val bankCode: String,

    @field:NotBlank(message = "La referencia es obligatoria")
    @Schema(description = "Número de referencia de la transferencia", example = "98273615")
    val reference: String,

    @field:NotNull(message = "El monto es obligatorio")
    @field:DecimalMin("0.01")
    @Schema(description = "Monto exacto en Bolívares (VES)", example = "455.00")
    val amount: BigDecimal,

    @field:NotBlank(message = "El teléfono emisor es obligatorio")
    @Schema(description = "Teléfono del pagador que emitió la transferencia", example = "04141234567")
    val senderPhone: String,

    @field:NotBlank(message = "La cédula emisora es obligatoria")
    @Schema(description = "Cédula o RIF del pagador emisor", example = "V12345678")
    val senderNationalId: String
)

@Schema(description = "Registro de nuevo comercio")
data class CreateMerchantRequest(
    @field:NotBlank(message = "El nombre es obligatorio")
    @Schema(description = "Nombre comercial", example = "Mi Tienda Online C.A.")
    val name: String,

    @field:Email(message = "Correo electrónico inválido")
    @field:NotBlank(message = "El email es obligatorio")
    @Schema(description = "Correo del comercio", example = "contacto@mitienda.com")
    val email: String,

    @Schema(description = "URL para recepción de webhooks", example = "https://mitienda.com/api/webhooks/spidi")
    val webhookUrl: String? = null
)

@Schema(description = "Actualización de tasa de cambio oficial")
data class UpdateRateRequest(
    @field:NotNull
    @field:DecimalMin("0.01")
    @Schema(description = "Nueva tasa oficial USD/VES", example = "46.20")
    val newRate: BigDecimal
)
