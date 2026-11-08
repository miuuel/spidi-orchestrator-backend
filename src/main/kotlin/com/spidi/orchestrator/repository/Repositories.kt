package com.spidi.orchestrator.repository

import com.spidi.orchestrator.domain.BankNotification
import com.spidi.orchestrator.domain.Merchant
import com.spidi.orchestrator.domain.Payment
import com.spidi.orchestrator.domain.PaymentStatus
import com.spidi.orchestrator.domain.WebhookDelivery
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

@Repository
interface MerchantRepository : JpaRepository<Merchant, UUID> {
    fun findByApiKeyPublic(apiKeyPublic: String): Optional<Merchant>
    fun findByApiKeySecret(apiKeySecret: String): Optional<Merchant>
    fun findByEmail(email: String): Optional<Merchant>
}

@Repository
interface PaymentRepository : JpaRepository<Payment, UUID> {
    fun findByMerchantId(merchantId: UUID): List<Payment>
    fun findByIdempotencyKeyAndMerchantId(idempotencyKey: String, merchantId: UUID): Optional<Payment>

    @Query("""
        SELECT p FROM Payment p 
        WHERE p.status = 'PENDING' 
          AND p.amountVes = :amountVes 
          AND (
            p.providerReference = :reference 
            OR p.customerPhone = :phone 
            OR p.customerNationalId = :nationalId
          )
    """)
    fun findCandidateForReconciliation(
        amountVes: BigDecimal,
        reference: String,
        phone: String,
        nationalId: String
    ): List<Payment>

    fun findByStatusAndCustomerPhoneAndAmountVes(
        status: PaymentStatus,
        customerPhone: String,
        amountVes: BigDecimal
    ): List<Payment>
}

@Repository
interface BankNotificationRepository : JpaRepository<BankNotification, UUID> {
    fun findByReference(reference: String): Optional<BankNotification>
    fun findByReconciledFalse(): List<BankNotification>
}

@Repository
interface WebhookDeliveryRepository : JpaRepository<WebhookDelivery, UUID> {
    fun findByMerchantId(merchantId: UUID): List<WebhookDelivery>
    fun findByPaymentId(paymentId: UUID): List<WebhookDelivery>
}
