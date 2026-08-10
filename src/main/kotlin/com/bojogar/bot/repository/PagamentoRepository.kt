package com.bojogar.bot.repository

import com.bojogar.bot.entity.Pagamento
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PagamentoRepository : JpaRepository<Pagamento, UUID> {

    fun findByInscricaoId(inscricaoId: UUID): List<Pagamento>
}
