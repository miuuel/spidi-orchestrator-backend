package com.spidi.orchestrator.drivers

import com.spidi.orchestrator.domain.Payment
import com.spidi.orchestrator.domain.PaymentRail
import com.spidi.orchestrator.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Driver para Pago Móvil C2P (Comercio a Persona / Débito Inmediato con OTP).
 * En este riel, el cliente autoriza el débito en su banco mediante un token dinámico OTP.
 */
@Component
class PagoMovilC2PDriver : PaymentDriver {

    private val log = LoggerFactory.getLogger(javaClass)
    override val rail: PaymentRail = PaymentRail.PAGOMOVIL_C2P

    override fun initiate(payment: Payment, context: Map<String, Any>): DriverInitiateResult {
        log.info("Iniciando cobro C2P para pago: ${payment.id}, monto VES: ${payment.amountVes}")
        return DriverInitiateResult(
            status = PaymentStatus.PENDING,
            providerReference = null,
            instructions = mapOf(
                "action" to "ENTER_OTP",
                "message" to "Solicite su clave dinámica (OTP) en la aplicación o SMS de su banco e ingrésela para autorizar el débito inmediato.",
                "requiredFields" to listOf("bankCode", "customerPhone", "customerNationalId", "otp")
            )
        )
    }

    override fun confirm(payment: Payment, params: Map<String, Any>): DriverConfirmResult {
        val otp = params["otp"]?.toString()?.trim()
        val bankCode = params["bankCode"]?.toString()?.trim() ?: payment.customerBankCode

        log.info("Procesando débito C2P con banco $bankCode y OTP: ${otp?.take(2)}****")

        if (otp.isNullOrBlank()) {
            return DriverConfirmResult(
                success = false,
                status = PaymentStatus.FAILED,
                providerReference = null,
                errorMessage = "La clave dinámica (OTP) es obligatoria para procesar Pago Móvil C2P"
            )
        }

        // Simulación: '000000' simula fondos insuficientes o token expirado
        if (otp == "000000") {
            return DriverConfirmResult(
                success = false,
                status = PaymentStatus.FAILED,
                providerReference = null,
                errorMessage = "Rechazado por el banco emisor: Clave dinámica vencida o fondos insuficientes"
            )
        }

        // Generación de referencia interbancaria C2P (ej. 8 dígitos numéricos)
        val bankReference = "C2P-" + (10000000..99999999).random().toString()
        log.info("Cobro C2P aprobado con éxito. Referencia bancaria: $bankReference")

        return DriverConfirmResult(
            success = true,
            status = PaymentStatus.SUCCEEDED,
            providerReference = bankReference,
            errorMessage = null
        )
    }

    override fun cancel(payment: Payment): DriverCancelResult {
        return DriverCancelResult(true, "Cobro C2P cancelado antes de autorización")
    }
}
