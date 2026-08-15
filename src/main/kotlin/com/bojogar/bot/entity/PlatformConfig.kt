package com.bojogar.bot.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "platform_config")
class PlatformConfig(
    @Id
    @Column(nullable = false)
    val key: String = "SINGLETON",

    @Column(nullable = false)
    var minPrice: BigDecimal = BigDecimal("10.00"),

    @Column(nullable = false)
    var maxPrice: BigDecimal = BigDecimal("100.00"),

    @Column(nullable = false)
    var platformFeePercent: Int = 10,

    @Version
    var version: Long = 0
)
