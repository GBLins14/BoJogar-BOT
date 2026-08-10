package com.bojogar.bot.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var criadoEm: Instant? = null,

    @LastModifiedDate
    @Column(nullable = false)
    var atualizadoEm: Instant? = null
)
