package com.bojogar.bot.entity

import com.bojogar.bot.enums.StatusPagamento
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "pagamentos")
class Pagamento(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    val participant: PeladaParticipant,

    @Column(nullable = false, precision = 10, scale = 2)
    val valor: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: StatusPagamento = StatusPagamento.PENDENTE,

    var transactionId: String? = null,

    var paidAt: Instant? = null
) : BaseEntity()
