package com.spidi.orchestrator.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchants")
data class Merchant(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(name = "api_key_public", nullable = false, unique = true)
    var apiKeyPublic: String = "spidi_pub_" + UUID.randomUUID().toString().replace("-", "").take(24),

    @Column(name = "api_key_secret", nullable = false, unique = true)
    var apiKeySecret: String = "spidi_sec_" + UUID.randomUUID().toString().replace("-", ""),

    @Column(name = "webhook_url")
    var webhookUrl: String? = null,

    @Column(name = "webhook_secret", nullable = false)
    var webhookSecret: String = "whsec_" + UUID.randomUUID().toString().replace("-", ""),

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
