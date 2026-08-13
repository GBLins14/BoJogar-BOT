package com.bojogar.bot.entity

import com.bojogar.bot.enums.Esporte
import com.bojogar.bot.enums.StatusPelada
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "peladas")
class Pelada(
    @Column(nullable = false, unique = true, length = 10)
    val codigo: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizador_id", nullable = false)
    val createdBy: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val esporte: Esporte,

    var descricao: String? = null,

    @Column(nullable = false)
    var dataHora: LocalDateTime,

    @Column(nullable = false)
    var local: String,

    @Column(nullable = false)
    var limiteJogadores: Int,

    @Column(nullable = false, precision = 10, scale = 2)
    var valorPorJogador: BigDecimal = BigDecimal.ZERO,

    var chavePix: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: StatusPelada = StatusPelada.OPEN,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    var location: Location? = null,

    @Column(columnDefinition = "text")
    var sportConfig: String? = null,

    @Version
    var version: Long = 0
) : BaseEntity()
