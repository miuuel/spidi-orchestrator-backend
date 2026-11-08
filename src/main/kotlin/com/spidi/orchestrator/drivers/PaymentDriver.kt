package com.spidi.orchestrator.drivers

import com.spidi.orchestrator.domain.Payment
import com.spidi.orchestrator.domain.PaymentRail
import com.spidi.orchestrator.domain.PaymentStatus

data class DriverInitiateResult(
    val status: PaymentStatus,
    val providerReference: String?,
    val instructions: Map<String, Any> = emptyMap(),
    val errorMessage: String? = null
)

data class DriverConfirmResult(
    val success: Boolean,
    val status: PaymentStatus,
    val providerReference: String?,
    val errorMessage: String? = null
)

data class DriverCancelResult(
    val success: Boolean,
    val message: String
)

interface PaymentDriver {
    val rail: PaymentRail

    /**
     * Inicia el proceso de cobro en el riel seleccionado.
     */
    fun initiate(payment: Payment, context: Map<String, Any> = emptyMap()): DriverInitiateResult

    /**
     * Confirma el pago (ej. ingreso de OTP dinámico en C2P o hash en Cripto).
     */
    fun confirm(payment: Payment, params: Map<String, Any> = emptyMap()): DriverConfirmResult

    /**
     * Cancela un cobro si el riel lo soporta.
     */
    fun cancel(payment: Payment): DriverCancelResult
}
