package com.bojogar.bot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "locations")
class Location(
    @Column(nullable = false)
    val name: String,

    var address: String? = null,
    var city: String? = null,
    var state: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null
) : BaseEntity()
