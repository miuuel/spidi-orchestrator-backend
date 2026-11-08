package com.spidi.orchestrator.drivers

import com.spidi.orchestrator.domain.Payment
import com.spidi.orchestrator.domain.PaymentRail
import com.spidi.orchestrator.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Driver para Pago Móvil P2C (Persona a Comercio con Conciliación Automática).
 * El cliente emite un Pago Móvil a los datos del comercio, y el sistema concilia
 * en tiempo real la referencia y monto recibido sin intervención manual humana.
 */
@Component
class PagoMovilP2CDriver(
    @Value("\${spidi.p2c.bank-code:0137}")
    private val merchantBankCode: String,
    @Value("\${spidi.p2c.bank-name:Banco Sofitasa}")
    private val merchantBankName: String,
    @Value("\${spidi.p2c.merchant-national-id:J-501234567}")
    private val merchantNationalId: String,
    @Value("\${spidi.p2c.merchant-phone:04141234567}")
    private val merchantPhone: String
) : PaymentDriver {

    private val log = LoggerFactory.getLogger(javaClass)
    override val rail: PaymentRail = PaymentRail.PAGOMOVIL_P2C

    override fun initiate(payment: Payment, context: Map<String, Any>): DriverInitiateResult {
        log.info("Generando instrucciones P2C para pago ${payment.id}, monto VES: ${payment.amountVes}")

        val instructions = mapOf(
            "bankCode" to merchantBankCode,
            "bankName" to merchantBankName,
            "nationalId" to merchantNationalId,
            "phone" to merchantPhone,
            "amountVes" to payment.amountVes,
            "instructions" to "Realice su Pago Móvil con estos datos e ingrese el número de referencia para confirmación instantánea."
        )

        return DriverInitiateResult(
            status = PaymentStatus.PENDING,
            providerReference = null,
            instructions = instructions
        )
    }

    override fun confirm(payment: Payment, params: Map<String, Any>): DriverConfirmResult {
        val reference = params["reference"]?.toString()?.trim()

        if (reference.isNullOrBlank()) {
            return DriverConfirmResult(
                success = false,
                status = PaymentStatus.PENDING,
                providerReference = null,
                errorMessage = "Debe proporcionar el número de referencia del Pago Móvil"
            )
        }

        log.info("Pago P2C ${payment.id} actualizado con referencia de usuario: $reference. Esperando matching bancario...")

        // Registramos la referencia en el pago. El motor de conciliación en tiempo real
        // completará el pago una vez que el banco emita la notificación correspondiente.
        return DriverConfirmResult(
            success = true,
            status = PaymentStatus.PROCESSING,
            providerReference = reference,
            errorMessage = null
        )
    }

    override fun cancel(payment: Payment): DriverCancelResult {
        return DriverCancelResult(true, "Pago P2C cancelado")
    }
}
