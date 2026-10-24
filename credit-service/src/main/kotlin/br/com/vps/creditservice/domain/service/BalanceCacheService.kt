package br.com.vps.creditservice.domain.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

@Service
class BalanceCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    @Qualifier("balanceCacheTtl") private val ttl: Duration,
) {
    private val log = LoggerFactory.getLogger(BalanceCacheService::class.java)

    companion object {
        private const val KEY_PREFIX = "partner:balance:"
    }

    fun get(partnerId: UUID): BigDecimal? =
        try {
            redisTemplate.opsForValue().get("$KEY_PREFIX$partnerId")?.let { BigDecimal(it) }
        } catch (e: Exception) {
            log.warn("Redis read failed for partner $partnerId, falling back to DB", e)
            null
        }

    fun set(
        partnerId: UUID,
        balance: BigDecimal,
    ) {
        try {
            redisTemplate.opsForValue().set("$KEY_PREFIX$partnerId", balance.toPlainString(), ttl)
        } catch (e: Exception) {
            log.warn("Redis write failed for partner $partnerId", e)
        }
    }

    fun evict(partnerId: UUID) {
        try {
            redisTemplate.delete("$KEY_PREFIX$partnerId")
        } catch (e: Exception) {
            log.warn("Redis evict failed for partner $partnerId", e)
        }
    }
}
