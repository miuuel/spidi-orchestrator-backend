package com.spidi.orchestrator.core

import com.spidi.orchestrator.domain.BankNotification
import com.spidi.orchestrator.domain.Payment
import com.spidi.orchestrator.domain.PaymentRail
import com.spidi.orchestrator.domain.PaymentStatus
import com.spidi.orchestrator.repository.BankNotificationRepository
import com.spidi.orchestrator.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

data class ReconciliationResult(
    val matched: Boolean,
    val paymentId: UUID?,
    val bankNotificationId: UUID,
    val reference: String,
    val amountVes: BigDecimal,
    val message: String
)

@Service
class ReconciliationEngine(
    private val paymentRepository: PaymentRepository,
    private val bankNotificationRepository: BankNotificationRepository,
    private val webhookDispatcher: WebhookDispatcher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Procesa una notificación bancaria entrante en tiempo real (ej. webhook del banco aliado o polling).
     * Cruza monto, teléfono, cédula y referencia contra pagos PENDING.
     */
    @Transactional
    fun processBankNotification(notification: BankNotification): ReconciliationResult {
        log.info("Iniciando conciliación para notificación bancaria ref: ${notification.reference}, monto VES: ${notification.amount}")

        // 1. Guardar la notificación bancaria en el libro
        val savedNotification = bankNotificationRepository.save(notification)

        // 2. Buscar pagos pendientes con monto exacto en VES
        val candidates = paymentRepository.findCandidateForReconciliation(
            amountVes = notification.amount,
            reference = notification.reference,
            phone = notification.senderPhone,
            nationalId = notification.senderNationalId
        )

        val matchedPayment = candidates.firstOrNull { payment ->
            payment.status == PaymentStatus.PENDING &&
                (payment.rail == PaymentRail.PAGOMOVIL_P2C || payment.rail == PaymentRail.PAGOMOVIL_C2P) &&
                (
                    payment.providerReference?.trim() == notification.reference.trim() ||
                        payment.customerPhone?.trim() == notification.senderPhone.trim() ||
                        payment.customerNationalId?.trim() == notification.senderNationalId.trim()
                )
        }

        if (matchedPayment != null) {
            log.info("¡Conciliación EXITOSA! Pago ${matchedPayment.id} vinculado con notificación bancaria ${savedNotification.reference}")

            matchedPayment.status = PaymentStatus.SUCCEEDED
            matchedPayment.providerReference = notification.reference
            paymentRepository.save(matchedPayment)

            savedNotification.reconciled = true
            savedNotification.reconciledPaymentId = matchedPayment.id
            bankNotificationRepository.save(savedNotification)

            // Despachar webhook de éxito al comercio en milisegundos
            webhookDispatcher.dispatchPaymentEvent("payment.succeeded", matchedPayment)

            return ReconciliationResult(
                matched = true,
                paymentId = matchedPayment.id,
                bankNotificationId = savedNotification.id,
                reference = notification.reference,
                amountVes = notification.amount,
                message = "Pago conciliado automáticamente en tiempo real."
            )
        }

        log.info("Notificación ${savedNotification.reference} almacenada sin coincidencia pendiente inmediata.")
        return ReconciliationResult(
            matched = false,
            paymentId = null,
            bankNotificationId = savedNotification.id,
            reference = notification.reference,
            amountVes = notification.amount,
            message = "Notificación registrada en el libro mayor. Esperando reporte de referencia del cliente."
        )
    }

    /**
     * Intenta conciliar un pago cuando el cliente reporta su referencia bancaria desde el Checkout.
     */
    @Transactional
    fun reconcilePaymentWithExistingNotification(payment: Payment, reference: String): Boolean {
        val existingNotification = bankNotificationRepository.findByReference(reference).orElse(null)
        if (existingNotification != null && !existingNotification.reconciled) {
            // Verificar que el monto coincida
            if (existingNotification.amount.compareTo(payment.amountVes) == 0) {
                log.info("Pago ${payment.id} conciliado exitosamente con notificación bancaria previa ${existingNotification.reference}")

                payment.status = PaymentStatus.SUCCEEDED
                payment.providerReference = existingNotification.reference
                paymentRepository.save(payment)

                existingNotification.reconciled = true
                existingNotification.reconciledPaymentId = payment.id
                bankNotificationRepository.save(existingNotification)

                webhookDispatcher.dispatchPaymentEvent("payment.succeeded", payment)
                return true
            }
        }
        return false
    }
}
