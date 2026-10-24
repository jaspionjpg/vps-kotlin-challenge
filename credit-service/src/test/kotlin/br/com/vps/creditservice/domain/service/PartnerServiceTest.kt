package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.PartnerAlreadyExistsException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundByExternalIdException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.resource.model.PartnerEntity
import br.com.vps.creditservice.resource.repositories.PartnerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class PartnerServiceTest {
    private val partnerRepository: PartnerRepository = mock()
    private val balanceCacheService: BalanceCacheService = mock()
    private lateinit var partnerService: PartnerService

    private val partnerId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        partnerService = PartnerService(partnerRepository, balanceCacheService)
    }

    @Test
    fun `findById should return partner when exists`() {
        val partner = PartnerEntity(externalId = "ext-1", name = "Partner 1")
        whenever(partnerRepository.findById(partnerId)).thenReturn(Optional.of(partner))

        val result = partnerService.findById(partnerId)

        assertEquals("Partner 1", result.name)
    }

    @Test
    fun `findById should throw PartnerNotFoundException when not exists`() {
        whenever(partnerRepository.findById(partnerId)).thenReturn(Optional.empty())

        assertThrows<PartnerNotFoundException> {
            partnerService.findById(partnerId)
        }
    }

    @Test
    fun `findByExternalId should return partner when exists`() {
        val partner = PartnerEntity(externalId = "ext-1", name = "Partner 1")
        whenever(partnerRepository.findByExternalId("ext-1")).thenReturn(partner)

        val result = partnerService.findByExternalId("ext-1")

        assertEquals("Partner 1", result.name)
    }

    @Test
    fun `findByExternalId should throw PartnerNotFoundByExternalIdException when not exists`() {
        whenever(partnerRepository.findByExternalId("missing")).thenReturn(null)

        assertThrows<PartnerNotFoundByExternalIdException> {
            partnerService.findByExternalId("missing")
        }
    }

    @Test
    fun `getBalance should return from cache when available`() {
        whenever(balanceCacheService.get(partnerId)).thenReturn(BigDecimal("100.00"))

        val result = partnerService.getBalance(partnerId)

        assertEquals(BigDecimal("100.00"), result)
        verify(partnerRepository, never()).findById(any())
    }

    @Test
    fun `getBalance should fetch from DB and populate cache on miss`() {
        val partner = PartnerEntity(externalId = "ext-1", name = "Partner 1", balance = BigDecimal("250.00"))
        whenever(balanceCacheService.get(partnerId)).thenReturn(null)
        whenever(partnerRepository.findById(partnerId)).thenReturn(Optional.of(partner))

        val result = partnerService.getBalance(partnerId)

        assertEquals(BigDecimal("250.00"), result)
        verify(balanceCacheService).set(partnerId, BigDecimal("250.00"))
    }

    @Test
    fun `createPartner should save and return new partner`() {
        whenever(partnerRepository.findByExternalId("ext-1")).thenReturn(null)
        whenever(partnerRepository.save(any<PartnerEntity>())).thenAnswer { it.arguments[0] }

        val result = partnerService.createPartner("ext-1", "New Partner")

        assertEquals("ext-1", result.externalId)
        assertEquals("New Partner", result.name)
    }

    @Test
    fun `createPartner should throw when externalId already exists`() {
        val existing = PartnerEntity(externalId = "ext-1", name = "Existing")
        whenever(partnerRepository.findByExternalId("ext-1")).thenReturn(existing)

        assertThrows<PartnerAlreadyExistsException> {
            partnerService.createPartner("ext-1", "Duplicate")
        }
    }

    @Test
    fun `addBalance should succeed and evict cache`() {
        whenever(partnerRepository.addBalance(partnerId, BigDecimal("50.00"))).thenReturn(1)

        partnerService.addBalance(partnerId, BigDecimal("50.00"))

        verify(balanceCacheService).evict(partnerId)
    }

    @Test
    fun `addBalance should throw PartnerNotFoundException when no rows affected`() {
        whenever(partnerRepository.addBalance(partnerId, BigDecimal("50.00"))).thenReturn(0)

        assertThrows<PartnerNotFoundException> {
            partnerService.addBalance(partnerId, BigDecimal("50.00"))
        }
    }

    @Test
    fun `subtractBalance should succeed and evict cache`() {
        whenever(partnerRepository.subtractBalance(partnerId, BigDecimal("30.00"))).thenReturn(1)

        partnerService.subtractBalance(partnerId, BigDecimal("30.00"))

        verify(balanceCacheService).evict(partnerId)
    }

    @Test
    fun `subtractBalance should throw InsufficientBalanceException when balance too low`() {
        whenever(partnerRepository.subtractBalance(partnerId, BigDecimal("999.00"))).thenReturn(0)
        whenever(partnerRepository.existsById(partnerId)).thenReturn(true)

        assertThrows<InsufficientBalanceException> {
            partnerService.subtractBalance(partnerId, BigDecimal("999.00"))
        }
    }

    @Test
    fun `subtractBalance should throw PartnerNotFoundException when partner not exists`() {
        whenever(partnerRepository.subtractBalance(partnerId, BigDecimal("10.00"))).thenReturn(0)
        whenever(partnerRepository.existsById(partnerId)).thenReturn(false)

        assertThrows<PartnerNotFoundException> {
            partnerService.subtractBalance(partnerId, BigDecimal("10.00"))
        }
    }
}
