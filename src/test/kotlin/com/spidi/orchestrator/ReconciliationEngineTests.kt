package com.spidi.orchestrator

import com.spidi.orchestrator.core.ReconciliationEngine
import com.spidi.orchestrator.core.WebhookDispatcher
import com.spidi.orchestrator.domain.*
import com.spidi.orchestrator.repository.BankNotificationRepository
import com.spidi.orchestrator.repository.PaymentRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.math.BigDecimal
import java.util.UUID
import com.spidi.orchestrator.domain.Currency

class ReconciliationEngineTests {

    private val paymentRepository = mock(PaymentRepository::class.java)
    private val bankNotificationRepository = mock(BankNotificationRepository::class.java)
    private val webhookDispatcher = mock(WebhookDispatcher::class.java)

    private val engine = ReconciliationEngine(paymentRepository, bankNotificationRepository, webhookDispatcher)

    @Test
    fun `test successful automated reconciliation of pending P2C payment`() {
        val merchantId = UUID.randomUUID()
        val pendingPayment = Payment(
            merchantId = merchantId,
            amount = BigDecimal("10.00"),
            currency = Currency.USD,
            amountVes = BigDecimal("455.00"),
            exchangeRate = BigDecimal("45.50"),
            rail = PaymentRail.PAGOMOVIL_P2C,
            status = PaymentStatus.PENDING,
            customerPhone = "04141234567",
            customerNationalId = "V12345678",
            providerReference = "REF987654"
        )

        val notification = BankNotification(
            bankCode = "0137",
            reference = "REF987654",
            amount = BigDecimal("455.00"),
            senderPhone = "04141234567",
            senderNationalId = "V12345678"
        )

        `when`(bankNotificationRepository.save(any(BankNotification::class.java))).thenAnswer { it.arguments[0] }
        `when`(paymentRepository.save(any(Payment::class.java))).thenAnswer { it.arguments[0] }
        `when`(
            paymentRepository.findCandidateForReconciliation(
                BigDecimal("455.00"),
                "REF987654",
                "04141234567",
                "V12345678"
            )
        ).thenReturn(listOf(pendingPayment))

        val result = engine.processBankNotification(notification)

        assertTrue(result.matched, "La conciliación debe ser exitosa")
        assertEquals(pendingPayment.id, result.paymentId)
        assertEquals(PaymentStatus.SUCCEEDED, pendingPayment.status)
        assertEquals("REF987654", pendingPayment.providerReference)

        verify(webhookDispatcher, times(1)).dispatchPaymentEvent("payment.succeeded", pendingPayment)
    }
}
