package br.com.vps.creditservice.domain.service

import br.com.vps.creditservice.domain.exception.PartnerAlreadyExistsException
import br.com.vps.creditservice.domain.exception.PartnerNotFoundException
import br.com.vps.creditservice.resource.model.PartnerEntity
import br.com.vps.creditservice.resource.repositories.PartnerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class PartnerService(
    private val partnerRepository: PartnerRepository,
    private val balanceCacheService: BalanceCacheService
) {

    @Transactional(readOnly = true)
    fun findById(partnerId: String): PartnerEntity =
        partnerRepository.findById(partnerId)
            .orElseThrow { PartnerNotFoundException(partnerId) }

    @Transactional(readOnly = true)
    fun findByExternalId(externalId: String): PartnerEntity =
        partnerRepository.findByExternalId(externalId)
            ?: throw PartnerNotFoundException(externalId)

    @Transactional(readOnly = true)
    fun getBalance(partnerId: String): BigDecimal {
        balanceCacheService.get(partnerId)?.let { return it }
        val balance = findById(partnerId).balance
        balanceCacheService.set(partnerId, balance)
        return balance
    }

    @Transactional
    fun createPartner(externalId: String, name: String): PartnerEntity {
        partnerRepository.findByExternalId(externalId)?.let {
            throw PartnerAlreadyExistsException(externalId)
        }
        return partnerRepository.save(
            PartnerEntity(externalId = externalId, name = name)
        )
    }

    @Transactional
    fun findByIdForUpdate(partnerId: String): PartnerEntity =
        partnerRepository.findByIdForUpdate(partnerId)
            ?: throw PartnerNotFoundException(partnerId)
}
