package com.bojogar.bot.repository

import com.bojogar.bot.entity.Inscricao
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InscricaoRepository : JpaRepository<Inscricao, UUID> {

    fun findByPeladaId(peladaId: UUID): List<Inscricao>

    fun findByJogadorId(jogadorId: UUID): List<Inscricao>

    fun findByPeladaIdAndJogadorId(peladaId: UUID, jogadorId: UUID): Inscricao?
}
