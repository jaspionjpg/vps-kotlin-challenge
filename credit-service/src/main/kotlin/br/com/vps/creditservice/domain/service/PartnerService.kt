package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.InsufficientBalanceException
import br.com.vps.creditservice.domain.exception.PartnerAlreadyExistsException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundByExternalIdException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.resource.model.PartnerEntity
import br.com.vps.creditservice.resource.repositories.PartnerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class PartnerService(
    private val partnerRepository: PartnerRepository,
    private val balanceCacheService: BalanceCacheService,
) {
    @Transactional(readOnly = true)
    fun findById(partnerId: UUID): PartnerEntity =
        partnerRepository
            .findById(partnerId)
            .orElseThrow { PartnerNotFoundException(partnerId) }

    @Transactional(readOnly = true)
    fun findByExternalId(externalId: String): PartnerEntity =
        partnerRepository.findByExternalId(externalId)
            ?: throw PartnerNotFoundByExternalIdException(externalId)

    @Transactional(readOnly = true)
    fun getBalance(partnerId: UUID): BigDecimal {
        balanceCacheService.get(partnerId)?.let { return it }
        val balance = findById(partnerId).balance
        balanceCacheService.set(partnerId, balance)
        return balance
    }

    @Transactional
    fun createPartner(
        externalId: String,
        name: String,
    ): PartnerEntity {
        partnerRepository.findByExternalId(externalId)?.let {
            throw PartnerAlreadyExistsException(externalId)
        }
        return partnerRepository.save(
            PartnerEntity(externalId = externalId, name = name),
        )
    }

    fun addBalance(
        partnerId: UUID,
        amount: BigDecimal,
    ) {
        val rowsAffected = partnerRepository.addBalance(partnerId, amount)
        if (rowsAffected == 0) throw PartnerNotFoundException(partnerId)
        balanceCacheService.evict(partnerId)
    }

    fun subtractBalance(
        partnerId: UUID,
        amount: BigDecimal,
    ) {
        val rowsAffected = partnerRepository.subtractBalance(partnerId, amount)
        if (rowsAffected == 0) {
            partnerRepository.findBalanceById(partnerId)
                ?: throw PartnerNotFoundException(partnerId)
            throw InsufficientBalanceException(partnerId)
        }
        balanceCacheService.evict(partnerId)
    }
}
