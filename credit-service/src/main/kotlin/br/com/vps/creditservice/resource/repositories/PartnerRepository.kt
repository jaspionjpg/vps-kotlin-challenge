package br.com.vps.creditservice.resource.repositories

import br.com.vps.creditservice.resource.model.PartnerEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.UUID

interface PartnerRepository : JpaRepository<PartnerEntity, UUID> {

    fun findByExternalId(externalId: String): PartnerEntity?

    @Modifying
    @Query(
        "UPDATE partner SET balance = balance + :amount, updated_at = NOW() WHERE id = :id",
        nativeQuery = true
    )
    fun addBalance(@Param("id") id: UUID, @Param("amount") amount: BigDecimal): Int

    @Modifying
    @Query(
        "UPDATE partner SET balance = balance - :amount, updated_at = NOW() WHERE id = :id AND balance >= :amount",
        nativeQuery = true
    )
    fun subtractBalance(@Param("id") id: UUID, @Param("amount") amount: BigDecimal): Int
}
