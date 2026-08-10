package com.bojogar.bot.entity

import jakarta.persistence.*

@Entity
@Table(name = "jogadores")
class Jogador(
    @Column(nullable = false)
    var nome: String,

    @Column(nullable = false, unique = true)
    var telefone: String
) : BaseEntity()
