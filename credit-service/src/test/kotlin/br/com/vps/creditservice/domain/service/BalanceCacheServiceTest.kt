package br.com.vps.creditservice.domain.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class BalanceCacheServiceTest {
    private val redisTemplate: RedisTemplate<String, String> = mock()
    private val valueOps: ValueOperations<String, String> = mock()
    private val ttl = Duration.ofMinutes(30)
    private lateinit var cacheService: BalanceCacheService

    private val partnerId = UUID.randomUUID()
    private val cacheKey = "partner:balance:$partnerId"

    @BeforeEach
    fun setup() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        cacheService = BalanceCacheService(redisTemplate, ttl)
    }

    @Test
    fun `get should return cached balance`() {
        whenever(valueOps.get(cacheKey)).thenReturn("150.5000")

        val result = cacheService.get(partnerId)

        assertEquals(BigDecimal("150.5000"), result)
    }

    @Test
    fun `get should return null when cache miss`() {
        whenever(valueOps.get(cacheKey)).thenReturn(null)

        val result = cacheService.get(partnerId)

        assertNull(result)
    }

    @Test
    fun `get should return null when Redis throws exception`() {
        whenever(valueOps.get(cacheKey)).thenThrow(RuntimeException("Connection refused"))

        val result = cacheService.get(partnerId)

        assertNull(result)
    }

    @Test
    fun `set should store balance with TTL`() {
        cacheService.set(partnerId, BigDecimal("200.0000"))

        verify(valueOps).set(cacheKey, "200.0000", ttl)
    }

    @Test
    fun `set should not throw when Redis fails`() {
        whenever(valueOps.set(any(), any(), any<Duration>())).thenThrow(RuntimeException("Connection refused"))

        cacheService.set(partnerId, BigDecimal("100.00"))
    }

    @Test
    fun `evict should delete cache key`() {
        cacheService.evict(partnerId)

        verify(redisTemplate).delete(cacheKey)
    }

    @Test
    fun `evict should not throw when Redis fails`() {
        whenever(redisTemplate.delete(cacheKey)).thenThrow(RuntimeException("Connection refused"))

        cacheService.evict(partnerId)
    }
}
