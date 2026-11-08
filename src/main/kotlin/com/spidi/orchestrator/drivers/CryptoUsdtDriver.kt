package com.spidi.orchestrator.drivers

import com.spidi.orchestrator.domain.Payment
import com.spidi.orchestrator.domain.PaymentRail
import com.spidi.orchestrator.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Driver para Criptoactivos (USDT sobre red TRON TRC-20 / Binance Pay).
 * Permite al comprador pagar con stablecoins y al comercio recibir o liquidar en Bolívares o Cripto.
 */
@Component
class CryptoUsdtDriver : PaymentDriver {

    private val log = LoggerFactory.getLogger(javaClass)
    override val rail: PaymentRail = PaymentRail.CRYPTO_USDT

    // Dirección maestra del pool de cobro
    private val depositAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6tSpidi"

    override fun initiate(payment: Payment, context: Map<String, Any>): DriverInitiateResult {
        // En USDT, si el monto original era en USD, es 1:1; si era en VES, se calcula al cambio
        val usdtAmount = if (payment.currency == com.spidi.orchestrator.domain.Currency.VES) {
            payment.amountVes.divide(payment.exchangeRate, 2, RoundingMode.HALF_UP)
        } else {
            payment.amount.setScale(2, RoundingMode.HALF_UP)
        }

        log.info("Iniciando cobro Cripto USDT para pago ${payment.id}: $usdtAmount USDT")

        val instructions = mapOf(
            "network" to "TRON (TRC-20)",
            "token" to "USDT",
            "depositAddress" to depositAddress,
            "amountUsdt" to usdtAmount,
            "qrCodeUri" to "tron:$depositAddress?amount=$usdtAmount",
            "instructions" to "Transfiera exactamente $usdtAmount USDT por la red TRC-20 e ingrese el Hash de la transacción (TxID)."
        )

        return DriverInitiateResult(
            status = PaymentStatus.PENDING,
            providerReference = null,
            instructions = instructions
        )
    }

    override fun confirm(payment: Payment, params: Map<String, Any>): DriverConfirmResult {
        val txHash = params["txHash"]?.toString()?.trim()

        if (txHash.isNullOrBlank()) {
            return DriverConfirmResult(
                success = false,
                status = PaymentStatus.PENDING,
                providerReference = null,
                errorMessage = "Debe proporcionar el TxID de la transferencia de USDT"
            )
        }

        // Simulación: Si el hash contiene 'fail' o es inválido, rechazar
        if (txHash.contains("fail", ignoreCase = true)) {
            return DriverConfirmResult(
                success = false,
                status = PaymentStatus.FAILED,
                providerReference = txHash,
                errorMessage = "Transacción blockchain no encontrada o revertida en la red TRON"
            )
        }

        log.info("Pago Cripto USDT ${payment.id} confirmado con TxID: $txHash")
        return DriverConfirmResult(
            success = true,
            status = PaymentStatus.SUCCEEDED,
            providerReference = txHash,
            errorMessage = null
        )
    }

    override fun cancel(payment: Payment): DriverCancelResult {
        return DriverCancelResult(true, "Cobro Cripto cancelado")
    }
}
