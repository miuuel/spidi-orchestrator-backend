package com.spidi.orchestrator

import com.spidi.orchestrator.domain.*
import com.spidi.orchestrator.drivers.CardPaymentDriver
import com.spidi.orchestrator.drivers.PagoMovilC2PDriver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class PaymentDriverTests {

    private val c2pDriver = PagoMovilC2PDriver()
    private val cardDriver = CardPaymentDriver()

    @Test
    fun `test C2P payment succeeds with valid OTP`() {
        val payment = Payment(
            merchantId = UUID.randomUUID(),
            amount = BigDecimal("10.00"),
            currency = Currency.USD,
            amountVes = BigDecimal("455.00"),
            exchangeRate = BigDecimal("45.50"),
            rail = PaymentRail.PAGOMOVIL_C2P
        )

        val confirmResult = c2pDriver.confirm(payment, mapOf("otp" to "123456", "bankCode" to "0102"))

        assertTrue(confirmResult.success)
        assertEquals(PaymentStatus.SUCCEEDED, confirmResult.status)
        assertNotNull(confirmResult.providerReference)
        assertTrue(confirmResult.providerReference!!.startsWith("C2P-"))
    }

    @Test
    fun `test C2P payment fails when OTP simulates expired or insufficient funds`() {
        val payment = Payment(
            merchantId = UUID.randomUUID(),
            amount = BigDecimal("10.00"),
            currency = Currency.USD,
            amountVes = BigDecimal("455.00"),
            exchangeRate = BigDecimal("45.50"),
            rail = PaymentRail.PAGOMOVIL_C2P
        )

        val confirmResult = c2pDriver.confirm(payment, mapOf("otp" to "000000", "bankCode" to "0102"))

        assertFalse(confirmResult.success)
        assertEquals(PaymentStatus.FAILED, confirmResult.status)
        assertNotNull(confirmResult.errorMessage)
    }

    @Test
    fun `test card payment validation with valid Luhn`() {
        val payment = Payment(
            merchantId = UUID.randomUUID(),
            amount = BigDecimal("15.00"),
            currency = Currency.USD,
            amountVes = BigDecimal("682.50"),
            exchangeRate = BigDecimal("45.50"),
            rail = PaymentRail.CARD
        )

        // Valid card number passing Luhn check
        val result = cardDriver.confirm(
            payment,
            mapOf(
                "cardNumber" to "4532012345678910",
                "expMonth" to 12,
                "expYear" to 2028,
                "cvv" to "123"
            )
        )

        assertTrue(result.success)
        assertEquals(PaymentStatus.SUCCEEDED, result.status)
        assertTrue(result.providerReference!!.startsWith("AUTH-"))
    }

    @Test
    fun `test card payment fails with invalid card number`() {
        val payment = Payment(
            merchantId = UUID.randomUUID(),
            amount = BigDecimal("15.00"),
            currency = Currency.USD,
            amountVes = BigDecimal("682.50"),
            exchangeRate = BigDecimal("45.50"),
            rail = PaymentRail.CARD
        )

        val result = cardDriver.confirm(
            payment,
            mapOf(
                "cardNumber" to "4532012345678919", // Invalid Luhn
                "expMonth" to 12,
                "expYear" to 2028,
                "cvv" to "123"
            )
        )

        assertFalse(result.success)
        assertEquals(PaymentStatus.FAILED, result.status)
    }
}
