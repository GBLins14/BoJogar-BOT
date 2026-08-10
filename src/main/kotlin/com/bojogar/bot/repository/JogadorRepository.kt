package com.bojogar.bot.repository

import com.bojogar.bot.entity.Jogador
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JogadorRepository : JpaRepository<Jogador, UUID> {

    fun findByTelefone(telefone: String): Jogador?
}
