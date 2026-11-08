package com.spidi.orchestrator.core

import com.spidi.orchestrator.domain.Currency
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

@Service
class ExchangeRateService(
    @Value("\${spidi.exchange-rate.default-usd-ves:45.50}")
    private val defaultRate: Double,
    @Value("\${spidi.exchange-rate.cache-ttl-minutes:60}")
    private val cacheTtlMinutes: Long,
    private val redisTemplate: RedisTemplate<String, Any>?
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val redisKey = "spidi:rate:bcv:usd_ves"
    private var localCachedRate: BigDecimal = BigDecimal.valueOf(defaultRate)

    fun getUsdToVesRate(): BigDecimal {
        try {
            if (redisTemplate != null) {
                val cached = redisTemplate.opsForValue().get(redisKey)
                if (cached != null) {
                    return BigDecimal(cached.toString()).setScale(4, RoundingMode.HALF_UP)
                }
            }
        } catch (ex: Exception) {
            log.warn("Error consultando tasa en Redis, usando caché local: ${ex.message}")
        }
        return localCachedRate.setScale(4, RoundingMode.HALF_UP)
    }

    fun updateUsdToVesRate(newRate: BigDecimal): BigDecimal {
        val rate = newRate.setScale(4, RoundingMode.HALF_UP)
        localCachedRate = rate
        try {
            redisTemplate?.opsForValue()?.set(redisKey, rate.toString(), Duration.ofMinutes(cacheTtlMinutes))
        } catch (ex: Exception) {
            log.warn("No se pudo actualizar tasa en Redis: ${ex.message}")
        }
        log.info("Tasa de cambio USD/VES actualizada a: $rate")
        return rate
    }

    /**
     * Convierte cualquier monto de origen al equivalente en Bolívares (VES).
     */
    fun convertToVes(amount: BigDecimal, currency: Currency): BigDecimal {
        val currentRate = getUsdToVesRate()
        return when (currency) {
            Currency.VES -> amount.setScale(2, RoundingMode.HALF_UP)
            Currency.USD, Currency.USDT -> amount.multiply(currentRate).setScale(2, RoundingMode.HALF_UP)
        }
    }
}
