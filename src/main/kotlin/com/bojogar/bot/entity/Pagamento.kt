package com.bojogar.bot.entity

import com.bojogar.bot.enums.StatusPagamento
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "pagamentos")
class Pagamento(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inscricao_id", nullable = false)
    val inscricao: Inscricao,

    @Column(nullable = false, precision = 10, scale = 2)
    val valor: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: StatusPagamento = StatusPagamento.PENDENTE,

    var transactionId: String? = null
) : BaseEntity()
