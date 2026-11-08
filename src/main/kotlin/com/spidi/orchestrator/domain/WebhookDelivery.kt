package com.spidi.orchestrator.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "webhook_deliveries",
    indexes = [
        Index(name = "idx_webhook_merchant", columnList = "merchant_id"),
        Index(name = "idx_webhook_payment", columnList = "payment_id")
    ]
)
data class WebhookDelivery(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "payment_id", nullable = false)
    val paymentId: UUID,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: UUID,

    @Column(nullable = false, length = 50)
    val event: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "http_status")
    var httpStatus: Int? = null,

    @Column(name = "response_body", columnDefinition = "TEXT")
    var responseBody: String? = null,

    @Column(nullable = false)
    var success: Boolean = false,

    @Column(nullable = false)
    var attempts: Int = 1,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
