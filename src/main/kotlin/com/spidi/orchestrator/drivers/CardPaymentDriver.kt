package com.spidi.orchestrator.drivers

import com.spidi.orchestrator.domain.Payment
import com.spidi.orchestrator.domain.PaymentRail
import com.spidi.orchestrator.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Driver para procesamiento de Tarjetas de Débito y Crédito (Nacionales e Internacionales).
 */
@Component
class CardPaymentDriver : PaymentDriver {

    private val log = LoggerFactory.getLogger(javaClass)
    override val rail: PaymentRail = PaymentRail.CARD

    override fun initiate(payment: Payment, context: Map<String, Any>): DriverInitiateResult {
        log.info("Iniciando cobro con tarjeta para pago ${payment.id}")
        return DriverInitiateResult(
            status = PaymentStatus.PENDING,
            providerReference = null,
            instructions = mapOf(
                "action" to "ENTER_CARD_DATA",
                "message" to "Ingrese los datos de su tarjeta de débito o crédito"
            )
        )
    }

    override fun confirm(payment: Payment, params: Map<String, Any>): DriverConfirmResult {
        val cardNumber = params["cardNumber"]?.toString()?.replace("\\s+".toRegex(), "") ?: ""
        val expMonth = params["expMonth"]?.toString()?.toIntOrNull()
        val expYear = params["expYear"]?.toString()?.toIntOrNull()
        val cvv = params["cvv"]?.toString()?.trim() ?: ""

        if (cardNumber.length < 13 || !luhnCheck(cardNumber)) {
            return DriverConfirmResult(
                success = false,
                status = PaymentStatus.FAILED,
                providerReference = null,
                errorMessage = "Número de tarjeta inválido (falla validación Luhn)"
            )
        }

        if (expMonth == null || expMonth !in 1..12 || expYear == null || expYear < 2024) {
            return DriverConfirmResult(
                success = false,
                status = PaymentStatus.FAILED,
                providerReference = null,
                errorMessage = "Fecha de expiración de tarjeta vencida o inválida"
            )
        }

        if (cvv.length !in 3..4) {
            return DriverConfirmResult(
                success = false,
                status = PaymentStatus.FAILED,
                providerReference = null,
                errorMessage = "Código de seguridad CVV inválido"
            )
        }

        // Simulación: tarjetas terminadas en 0000 simulan fondos insuficientes
        if (cardNumber.endsWith("0000")) {
            return DriverConfirmResult(
                success = false,
                status = PaymentStatus.FAILED,
                providerReference = null,
                errorMessage = "Transacción denegada: Fondos insuficientes en la cuenta del tarjetahabiente"
            )
        }

        val authCode = "AUTH-" + (100000..999999).random()
        log.info("Pago con tarjeta exitoso para ${payment.id}. Auth: $authCode")

        return DriverConfirmResult(
            success = true,
            status = PaymentStatus.SUCCEEDED,
            providerReference = authCode,
            errorMessage = null
        )
    }

    override fun cancel(payment: Payment): DriverCancelResult {
        return DriverCancelResult(true, "Cobro con tarjeta cancelado")
    }

    private fun luhnCheck(cardNumber: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in cardNumber.length - 1 downTo 0) {
            var n = cardNumber[i].digitToIntOrNull() ?: return false
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
