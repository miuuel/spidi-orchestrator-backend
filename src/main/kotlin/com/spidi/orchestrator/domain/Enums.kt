package com.spidi.orchestrator.domain

enum class PaymentRail(val displayName: String, val description: String) {
    PAGOMOVIL_C2P("Pago Móvil C2P", "Débito bancario directo con token dinámico OTP"),
    PAGOMOVIL_P2C("Pago Móvil P2C", "Transferencia móvil con conciliación automática por referencia"),
    CRYPTO_USDT("Cripto USDT", "Pago en stablecoin con liquidación automatizada"),
    CARD("Tarjeta de Débito / Crédito", "Procesamiento de tarjeta con tokenización")
}

enum class PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    EXPIRED
}

enum class Currency {
    USD,
    VES,
    USDT
}
