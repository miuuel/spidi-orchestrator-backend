package com.spidi.orchestrator.api

import com.spidi.orchestrator.domain.PaymentStatus
import com.spidi.orchestrator.repository.MerchantRepository
import com.spidi.orchestrator.repository.PaymentRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import java.util.UUID

@Controller
@Tag(name = "Checkout", description = "Hosted Checkout unificado para clientes finales")
class CheckoutController(
    private val paymentRepository: PaymentRepository,
    private val merchantRepository: MerchantRepository
) {

    @GetMapping("/checkout/{id}")
    fun renderCheckoutPage(@PathVariable id: UUID): String {
        return "forward:/checkout.html"
    }

    @GetMapping("/api/v1/checkout/{id}")
    @ResponseBody
    @Operation(summary = "Obtener datos del Checkout", description = "Provee los detalles del cobro y comercio para renderizar la pantalla de pago.")
    fun getCheckoutData(@PathVariable id: UUID): ResponseEntity<Map<String, Any?>> {
        val payment = paymentRepository.findById(id)
            .orElseThrow { NoSuchElementException("Pago no encontrado: $id") }

        val merchant = merchantRepository.findById(payment.merchantId).orElse(null)

        val data = mapOf(
            "id" to payment.id,
            "merchantName" to (merchant?.name ?: "Comercio Afiliado Spidi"),
            "amount" to payment.amount,
            "currency" to payment.currency.name,
            "amountVes" to payment.amountVes,
            "exchangeRate" to payment.exchangeRate,
            "rail" to payment.rail.name,
            "status" to payment.status.name,
            "description" to payment.description,
            "customerPhone" to payment.customerPhone,
            "customerNationalId" to payment.customerNationalId,
            "customerBankCode" to payment.customerBankCode,
            "providerReference" to payment.providerReference,
            "isCompleted" to (payment.status == PaymentStatus.SUCCEEDED)
        )

        return ResponseEntity.ok(data)
    }
}
