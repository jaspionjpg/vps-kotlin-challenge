package br.com.vps.creditservice.resource.repositories

import br.com.vps.creditservice.resource.model.PartnerEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface PartnerRepository : JpaRepository<PartnerEntity, String> {

    fun findByExternalId(externalId: String): PartnerEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PartnerEntity p WHERE p.id = :id")
    fun findByIdForUpdate(id: String): PartnerEntity?
}
