package com.bojogar.bot.repository

import com.bojogar.bot.entity.Pelada
import com.bojogar.bot.enums.StatusPelada
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface PeladaRepository : JpaRepository<Pelada, UUID> {

    fun findByCodigo(codigo: String): Pelada?

    fun findByCreatedByPhone(phone: String): List<Pelada>

    fun findByStatusIn(statuses: List<StatusPelada>): List<Pelada>

    fun findByStatusInAndDataHoraAfter(statuses: List<StatusPelada>, after: LocalDateTime): List<Pelada>

    fun findByStatusInAndDataHoraBefore(statuses: List<StatusPelada>, before: LocalDateTime): List<Pelada>
}
