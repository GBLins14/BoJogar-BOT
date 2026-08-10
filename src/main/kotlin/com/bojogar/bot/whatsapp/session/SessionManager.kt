package com.bojogar.bot.whatsapp.session

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class SessionManager {

    companion object {
        private val log = LoggerFactory.getLogger(SessionManager::class.java)
    }

    private val sessions = ConcurrentHashMap<String, ConversationSession>()

    fun getSession(phone: String): ConversationSession? {
        val session = sessions[phone] ?: return null
        if (session.isExpired()) {
            sessions.remove(phone)
            return null
        }
        return session
    }

    fun startCreatingPelada(phone: String) {
        sessions[phone] = ConversationSession(
            state = ConversationState.CREATING_PELADA,
            nextField = "esporte"
        )
    }

    fun startEnteringCode(phone: String) {
        sessions[phone] = ConversationSession(
            state = ConversationState.ENTERING_CODE
        )
    }

    fun setCurrentPelada(phone: String, peladaCode: String, state: ConversationState) {
        sessions[phone] = ConversationSession(
            state = state,
            currentPeladaCode = peladaCode
        )
    }

    fun updateSession(phone: String, key: String, value: String, nextField: String?) {
        val current = sessions[phone] ?: return
        sessions[phone] = current.withField(key, value, nextField)
    }

    fun updateSession(phone: String, session: ConversationSession) {
        sessions[phone] = session
    }

    fun clear(phone: String) {
        sessions.remove(phone)
    }

    @Scheduled(fixedRate = 300_000)
    fun cleanupExpired() {
        val before = sessions.size
        sessions.entries.removeIf { it.value.isExpired() }
        val removed = before - sessions.size
        if (removed > 0) {
            log.info("Session cleanup: removed {} expired sessions ({} active)", removed, sessions.size)
        }
    }
}
