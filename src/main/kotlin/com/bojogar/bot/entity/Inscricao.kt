package com.bojogar.bot.entity

import com.bojogar.bot.enums.StatusInscricao
import jakarta.persistence.*

@Entity
@Table(
    name = "inscricoes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["pelada_id", "jogador_id"])]
)
class Inscricao(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pelada_id", nullable = false)
    val pelada: Pelada,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jogador_id", nullable = false)
    val jogador: Jogador,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: StatusInscricao = StatusInscricao.CONFIRMADO,

    var posicaoListaEspera: Int? = null
) : BaseEntity()
