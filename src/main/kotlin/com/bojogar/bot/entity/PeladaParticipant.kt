package com.bojogar.bot.entity

import com.bojogar.bot.enums.ParticipantRole
import com.bojogar.bot.enums.ParticipantStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "pelada_participants",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "pelada_id"])]
)
class PeladaParticipant(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pelada_id", nullable = false)
    val pelada: Pelada,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: ParticipantRole = ParticipantRole.PLAYER,

    var displayName: String? = null,

    var shirtNumber: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ParticipantStatus = ParticipantStatus.CONFIRMED,

    var waitlistPosition: Int? = null,

    var joinedAt: Instant? = Instant.now()
) : BaseEntity()
