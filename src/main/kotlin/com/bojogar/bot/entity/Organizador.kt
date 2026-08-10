package com.bojogar.bot.entity

import jakarta.persistence.*

@Entity
@Table(name = "organizadores")
class Organizador(
    @Column(nullable = false)
    var nome: String,

    @Column(nullable = false, unique = true)
    var telefone: String,

    var email: String? = null
) : BaseEntity()
