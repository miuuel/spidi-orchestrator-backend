package com.spidi.orchestrator.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.spidi.orchestrator.domain.Merchant
import com.spidi.orchestrator.domain.Payment
import com.spidi.orchestrator.domain.WebhookDelivery
import com.spidi.orchestrator.repository.MerchantRepository
import com.spidi.orchestrator.repository.WebhookDeliveryRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class WebhookDispatcher(
    private val merchantRepository: MerchantRepository,
    private val webhookDeliveryRepository: WebhookDeliveryRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    /**
     * Genera una firma criptográfica HMAC-SHA256 para autenticar el webhook.
     * Formato estándar Spidi: t={timestamp},v1={hex_hmac}
     */
    fun computeHmacSignature(payload: String, secret: String, timestamp: Long = Instant.now().epochSecond): String {
        val message = "$timestamp.$payload"
        val signingKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(signingKey)
        }
        val hashBytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        val hashHex = hashBytes.joinToString("") { "%02x".format(it) }
        return "t=$timestamp,v1=$hashHex"
    }

    fun verifyHmacSignature(payload: String, secret: String, signatureHeader: String): Boolean {
        return try {
            val parts = signatureHeader.split(",")
            val tPart = parts.firstOrNull { it.startsWith("t=") }?.substring(2)?.toLongOrNull() ?: return false
            val v1Part = parts.firstOrNull { it.startsWith("v1=") }?.substring(3) ?: return false

            val expectedSig = computeHmacSignature(payload, secret, tPart)
            val expectedV1 = expectedSig.split(",").firstOrNull { it.startsWith("v1=") }?.substring(3)
            v1Part.equals(expectedV1, ignoreCase = true)
        } catch (ex: Exception) {
            false
        }
    }

    fun dispatchPaymentEvent(event: String, payment: Payment) {
        val merchant = merchantRepository.findById(payment.merchantId).orElse(null)
        if (merchant == null) {
            log.warn("No se encontró el comercio ${payment.merchantId} para enviar webhook.")
            return
        }

        val webhookUrl = merchant.webhookUrl
        val payloadMap = mapOf(
            "event" to event,
            "timestamp" to Instant.now().toString(),
            "data" to mapOf(
                "paymentId" to payment.id.toString(),
                "status" to payment.status.name,
                "amount" to payment.amount,
                "currency" to payment.currency.name,
                "amountVes" to payment.amountVes,
                "rail" to payment.rail.name,
                "providerReference" to payment.providerReference,
                "customerPhone" to payment.customerPhone,
                "customerNationalId" to payment.customerNationalId,
                "idempotencyKey" to payment.idempotencyKey,
                "createdAt" to payment.createdAt.toString(),
                "updatedAt" to payment.updatedAt.toString()
            )
        )

        val payloadJson = objectMapper.writeValueAsString(payloadMap)

        if (webhookUrl.isNullOrBlank()) {
            log.info("Comercio ${merchant.id} no tiene webhook_url configurada. Evento registrado localmente.")
            webhookDeliveryRepository.save(
                WebhookDelivery(
                    paymentId = payment.id,
                    merchantId = merchant.id,
                    event = event,
                    payload = payloadJson,
                    httpStatus = null,
                    responseBody = "NO_WEBHOOK_URL_CONFIGURED",
                    success = false
                )
            )
            return
        }

        val timestamp = Instant.now().epochSecond
        val signature = computeHmacSignature(payloadJson, merchant.webhookSecret, timestamp)

        try {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-Spidi-Signature", signature)
                set("User-Agent", "Spidi-Webhook-Dispatcher/1.0")
            }

            val request = HttpEntity(payloadJson, headers)
            val response = restTemplate.postForEntity(webhookUrl, request, String::class.java)

            val success = response.statusCode.is2xxSuccessful
            webhookDeliveryRepository.save(
                WebhookDelivery(
                    paymentId = payment.id,
                    merchantId = merchant.id,
                    event = event,
                    payload = payloadJson,
                    httpStatus = response.statusCode.value(),
                    responseBody = response.body?.take(1000),
                    success = success
                )
            )
            log.info("Webhook $event enviado exitosamente a $webhookUrl para pago ${payment.id}")
        } catch (ex: Exception) {
            log.error("Error despachando webhook $event a $webhookUrl: ${ex.message}")
            webhookDeliveryRepository.save(
                WebhookDelivery(
                    paymentId = payment.id,
                    merchantId = merchant.id,
                    event = event,
                    payload = payloadJson,
                    httpStatus = 500,
                    responseBody = ex.message?.take(1000),
                    success = false
                )
            )
        }
    }
}
