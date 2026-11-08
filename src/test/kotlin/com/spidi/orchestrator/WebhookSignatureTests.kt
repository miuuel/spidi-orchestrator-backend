package com.spidi.orchestrator

import com.fasterxml.jackson.databind.ObjectMapper
import com.spidi.orchestrator.core.WebhookDispatcher
import com.spidi.orchestrator.repository.MerchantRepository
import com.spidi.orchestrator.repository.WebhookDeliveryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class WebhookSignatureTests {

    private val merchantRepository = mock(MerchantRepository::class.java)
    private val webhookDeliveryRepository = mock(WebhookDeliveryRepository::class.java)
    private val objectMapper = ObjectMapper()

    private val dispatcher = WebhookDispatcher(merchantRepository, webhookDeliveryRepository, objectMapper)

    @Test
    fun `test compute and verify valid HMAC SHA256 signature`() {
        val payload = """{"event":"payment.succeeded","data":{"paymentId":"test-123","amount":10.0}}"""
        val secret = "whsec_super_secret_key_12345"
        val timestamp = 1730919000L

        val signatureHeader = dispatcher.computeHmacSignature(payload, secret, timestamp)

        assertTrue(signatureHeader.startsWith("t=1730919000,v1="))

        val isValid = dispatcher.verifyHmacSignature(payload, secret, signatureHeader)
        assertTrue(isValid, "La firma generada debe ser verificada como válida con el mismo secreto y payload")
    }

    @Test
    fun `test verify rejects altered payload or wrong secret`() {
        val payload = """{"event":"payment.succeeded","amount":10.0}"""
        val secret = "whsec_valid_secret"
        val timestamp = 1730919000L

        val signatureHeader = dispatcher.computeHmacSignature(payload, secret, timestamp)

        // Altered payload
        val alteredPayload = """{"event":"payment.succeeded","amount":999.0}"""
        assertFalse(dispatcher.verifyHmacSignature(alteredPayload, secret, signatureHeader))

        // Wrong secret
        assertFalse(dispatcher.verifyHmacSignature(payload, "whsec_wrong_secret", signatureHeader))
    }
}
