package com.spidi.orchestrator.core

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class IdempotencyService(
    private val redisTemplate: RedisTemplate<String, Any>?
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Fallback en memoria si Redis no está activo durante pruebas locales
    private val inMemoryLocks = ConcurrentHashMap<String, Instant>()
    private val inMemoryResponses = ConcurrentHashMap<String, Pair<String, Instant>>()

    fun acquireLock(idempotencyKey: String, merchantId: String, ttlSeconds: Long = 60): Boolean {
        val lockKey = "idemp:lock:$merchantId:$idempotencyKey"
        try {
            if (redisTemplate != null) {
                val acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "PROCESSING", Duration.ofSeconds(ttlSeconds))
                if (acquired == true) return true
            }
        } catch (ex: Exception) {
            log.warn("Redis no disponible para bloqueo de idempotencia. Usando fallback en memoria: ${ex.message}")
        }

        // Fallback local
        val now = Instant.now()
        val existingExpiry = inMemoryLocks[lockKey]
        if (existingExpiry != null && existingExpiry.isAfter(now)) {
            return false // Ya bloqueado
        }
        inMemoryLocks[lockKey] = now.plusSeconds(ttlSeconds)
        return true
    }

    fun saveResponse(idempotencyKey: String, merchantId: String, responseJson: String, ttlHours: Long = 24) {
        val cacheKey = "idemp:resp:$merchantId:$idempotencyKey"
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(cacheKey, responseJson, Duration.ofHours(ttlHours))
                return
            }
        } catch (ex: Exception) {
            log.warn("Redis no disponible para guardar respuesta idempotente: ${ex.message}")
        }

        inMemoryResponses[cacheKey] = Pair(responseJson, Instant.now().plus(Duration.ofHours(ttlHours)))
    }

    fun getCachedResponse(idempotencyKey: String, merchantId: String): String? {
        val cacheKey = "idemp:resp:$merchantId:$idempotencyKey"
        try {
            if (redisTemplate != null) {
                val cached = redisTemplate.opsForValue().get(cacheKey) as? String
                if (cached != null) return cached
            }
        } catch (ex: Exception) {
            log.warn("Redis no disponible para obtener respuesta en caché: ${ex.message}")
        }

        val entry = inMemoryResponses[cacheKey]
        if (entry != null && entry.second.isAfter(Instant.now())) {
            return entry.first
        }
        return null
    }

    fun releaseLock(idempotencyKey: String, merchantId: String) {
        val lockKey = "idemp:lock:$merchantId:$idempotencyKey"
        try {
            redisTemplate?.delete(lockKey)
        } catch (ex: Exception) {
            // Ignorar
        }
        inMemoryLocks.remove(lockKey)
    }
}
