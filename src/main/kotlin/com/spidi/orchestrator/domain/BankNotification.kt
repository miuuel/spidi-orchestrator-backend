package com.spidi.orchestrator.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Representa una notificación bancaria recibida (ej. webhook o extracto de cuenta bancaria del aliado).
 * Es la base de la conciliación automática en tiempo real de Spidi para eliminar la revisión de captures.
 */
@Entity
@Table(
    name = "bank_notifications",
    indexes = [
        Index(name = "idx_bank_notif_ref", columnList = "reference"),
        Index(name = "idx_bank_notif_reconciled", columnList = "reconciled")
    ]
)
data class BankNotification(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "bank_code", nullable = false, length = 10)
    val bankCode: String,

    @Column(nullable = false, length = 100)
    val reference: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "sender_phone", length = 30)
    val senderPhone: String,

    @Column(name = "sender_national_id", length = 30)
    val senderNationalId: String,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant = Instant.now(),

    @Column(nullable = false)
    var reconciled: Boolean = false,

    @Column(name = "reconciled_payment_id")
    var reconciledPaymentId: UUID? = null
)
