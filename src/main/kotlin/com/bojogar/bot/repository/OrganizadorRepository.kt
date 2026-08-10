package com.bojogar.bot.repository

import com.bojogar.bot.entity.Organizador
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrganizadorRepository : JpaRepository<Organizador, UUID> {

    fun findByTelefone(telefone: String): Organizador?
}
