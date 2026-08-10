package com.bojogar.bot.service

import com.bojogar.bot.repository.PagamentoRepository
import org.springframework.stereotype.Service

@Service
class PagamentoService(private val repository: PagamentoRepository)
