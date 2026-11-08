package com.spidi.orchestrator.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payments_merchant", columnList = "merchant_id"),
        Index(name = "idx_payments_status", columnList = "status"),
        Index(name = "idx_payments_idempotency", columnList = "idempotency_key"),
        Index(name = "idx_payments_phone_ref", columnList = "customer_phone, provider_reference")
    ]
)
data class Payment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_id", nullable = false)
    val merchantId: UUID,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val currency: Currency = Currency.USD,

    @Column(name = "amount_ves", nullable = false, precision = 18, scale = 2)
    var amountVes: BigDecimal,

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 4)
    val exchangeRate: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var rail: PaymentRail,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(length = 255)
    var description: String? = null,

    @Column(name = "customer_phone", length = 30)
    var customerPhone: String? = null,

    @Column(name = "customer_national_id", length = 30)
    var customerNationalId: String? = null,

    @Column(name = "customer_name", length = 100)
    var customerName: String? = null,

    @Column(name = "customer_bank_code", length = 10)
    var customerBankCode: String? = null,

    @Column(name = "provider_reference", length = 100)
    var providerReference: String? = null,

    @Column(name = "idempotency_key", length = 128)
    var idempotencyKey: String? = null,

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    var metadataJson: String? = null,

    @Column(name = "failure_reason", length = 255)
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
