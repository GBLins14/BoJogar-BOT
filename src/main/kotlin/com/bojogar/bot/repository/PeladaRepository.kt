package com.bojogar.bot.repository

import com.bojogar.bot.entity.Pelada
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PeladaRepository : JpaRepository<Pelada, UUID> {

    fun findByCodigo(codigo: String): Pelada?

    fun findByOrganizadorId(organizadorId: UUID): List<Pelada>
}
