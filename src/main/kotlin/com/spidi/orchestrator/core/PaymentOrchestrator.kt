package com.spidi.orchestrator.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.spidi.orchestrator.domain.*
import com.spidi.orchestrator.drivers.DriverInitiateResult
import com.spidi.orchestrator.drivers.PaymentDriver
import com.spidi.orchestrator.repository.MerchantRepository
import com.spidi.orchestrator.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

data class CreatePaymentCommand(
    val merchantId: UUID,
    val amount: BigDecimal,
    val currency: Currency = Currency.USD,
    val rail: PaymentRail,
    val description: String?,
    val customerPhone: String?,
    val customerNationalId: String?,
    val customerName: String?,
    val customerBankCode: String?,
    val idempotencyKey: String?,
    val metadata: Map<String, Any>? = null
)

data class PaymentExecutionResult(
    val payment: Payment,
    val instructions: Map<String, Any>,
    val cachedFromIdempotency: Boolean = false
)

@Service
class PaymentOrchestrator(
    private val paymentRepository: PaymentRepository,
    private val merchantRepository: MerchantRepository,
    private val drivers: List<PaymentDriver>,
    private val exchangeRateService: ExchangeRateService,
    private val idempotencyService: IdempotencyService,
    private val reconciliationEngine: ReconciliationEngine,
    private val webhookDispatcher: WebhookDispatcher,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val driverMap: Map<PaymentRail, PaymentDriver> by lazy {
        drivers.associateBy { it.rail }
    }

    @Transactional
    fun createPayment(cmd: CreatePaymentCommand): PaymentExecutionResult {
        // 1. Manejo de Idempotencia con Redis
        if (!cmd.idempotencyKey.isNullOrBlank()) {
            val cachedJson = idempotencyService.getCachedResponse(cmd.idempotencyKey, cmd.merchantId.toString())
            if (cachedJson != null) {
                log.info("Respuesta idempotente recuperada para clave: ${cmd.idempotencyKey}")
                val cachedPayment = objectMapper.readValue(cachedJson, Payment::class.java)
                return PaymentExecutionResult(
                    payment = cachedPayment,
                    instructions = mapOf("idempotent" to true),
                    cachedFromIdempotency = true
                )
            }

            val acquired = idempotencyService.acquireLock(cmd.idempotencyKey, cmd.merchantId.toString())
            if (!acquired) {
                throw IllegalStateException("Operación en proceso para la clave de idempotencia proporcionada.")
            }
        }

        // 2. Cálculo de divisas y tasa vigente
        val exchangeRate = exchangeRateService.getUsdToVesRate()
        val amountVes = exchangeRateService.convertToVes(cmd.amount, cmd.currency)

        // 3. Crear entidad de pago
        val payment = Payment(
            merchantId = cmd.merchantId,
            amount = cmd.amount,
            currency = cmd.currency,
            amountVes = amountVes,
            exchangeRate = exchangeRate,
            rail = cmd.rail,
            status = PaymentStatus.PENDING,
            description = cmd.description,
            customerPhone = cmd.customerPhone,
            customerNationalId = cmd.customerNationalId,
            customerName = cmd.customerName,
            customerBankCode = cmd.customerBankCode,
            idempotencyKey = cmd.idempotencyKey,
            metadataJson = cmd.metadata?.let { objectMapper.writeValueAsString(it) }
        )

        val savedPayment = paymentRepository.save(payment)

        // 4. Seleccionar Driver según Riel y ejecutar inicio
        val driver = driverMap[cmd.rail]
            ?: throw IllegalArgumentException("No se encontró driver para el riel: ${cmd.rail}")

        val initiateResult: DriverInitiateResult = driver.initiate(savedPayment)
        savedPayment.status = initiateResult.status
        savedPayment.providerReference = initiateResult.providerReference
        paymentRepository.save(savedPayment)

        // 5. Guardar en caché para futuras peticiones idempotentes
        if (!cmd.idempotencyKey.isNullOrBlank()) {
            val json = objectMapper.writeValueAsString(savedPayment)
            idempotencyService.saveResponse(cmd.idempotencyKey, cmd.merchantId.toString(), json)
        }

        return PaymentExecutionResult(
            payment = savedPayment,
            instructions = initiateResult.instructions
        )
    }

    @Transactional
    fun confirmPayment(paymentId: UUID, params: Map<String, Any>): Payment {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { NoSuchElementException("Pago no encontrado: $paymentId") }

        if (payment.status == PaymentStatus.SUCCEEDED) {
            log.info("El pago $paymentId ya se encuentra SUCCEEDED.")
            return payment
        }

        val driver = driverMap[payment.rail]
            ?: throw IllegalArgumentException("No se encontró driver para el riel: ${payment.rail}")

        // Caso especial Pago Móvil P2C: Si se envía referencia, intentar conciliación automática
        if (payment.rail == PaymentRail.PAGOMOVIL_P2C) {
            val reference = params["reference"]?.toString()?.trim()
            if (!reference.isNullOrBlank()) {
                payment.providerReference = reference
                val reconciled = reconciliationEngine.reconcilePaymentWithExistingNotification(payment, reference)
                if (reconciled) {
                    return payment
                }
            }
        }

        // Ejecutar confirmación con el driver
        val result = driver.confirm(payment, params)
        payment.status = result.status
        if (result.providerReference != null) {
            payment.providerReference = result.providerReference
        }
        payment.failureReason = result.errorMessage

        val updated = paymentRepository.save(payment)

        // Despachar webhook de evento
        if (updated.status == PaymentStatus.SUCCEEDED) {
            webhookDispatcher.dispatchPaymentEvent("payment.succeeded", updated)
        } else if (updated.status == PaymentStatus.FAILED) {
            webhookDispatcher.dispatchPaymentEvent("payment.failed", updated)
        }

        return updated
    }
}
