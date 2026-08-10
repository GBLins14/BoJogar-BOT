package com.bojogar.bot.service

import com.bojogar.bot.enums.StatusPelada
import com.bojogar.bot.repository.PeladaRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class PeladaScheduler(
    private val peladaRepository: PeladaRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(PeladaScheduler::class.java)
        private const val HOURS_TO_FINISH = 3L
    }

    @Scheduled(fixedRate = 900_000) // 15 minutes
    @Transactional
    fun autoTransitionStatuses() {
        val now = LocalDateTime.now()

        // OPEN/FULL peladas past their dateTime -> IN_PROGRESS
        val pastStart = peladaRepository.findByStatusInAndDataHoraBefore(
            listOf(StatusPelada.OPEN, StatusPelada.FULL),
            now
        )

        pastStart.forEach { pelada ->
            pelada.status = StatusPelada.IN_PROGRESS
            peladaRepository.save(pelada)
            log.info("Pelada {} auto-transitioned to IN_PROGRESS", pelada.codigo)
        }

        // IN_PROGRESS peladas past dateTime + HOURS_TO_FINISH -> FINISHED
        val pastFinish = peladaRepository.findByStatusInAndDataHoraBefore(
            listOf(StatusPelada.IN_PROGRESS),
            now.minusHours(HOURS_TO_FINISH)
        )

        pastFinish.forEach { pelada ->
            pelada.status = StatusPelada.FINISHED
            peladaRepository.save(pelada)
            log.info("Pelada {} auto-transitioned to FINISHED", pelada.codigo)
        }

        if (pastStart.isNotEmpty() || pastFinish.isNotEmpty()) {
            log.info("Auto-transition: {} started, {} finished", pastStart.size, pastFinish.size)
        }
    }
}
