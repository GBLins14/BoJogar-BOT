package com.bojogar.bot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    val phone: String,

    @Column(nullable = false)
    var name: String
) : BaseEntity()
