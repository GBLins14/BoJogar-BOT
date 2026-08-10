package com.bojogar.bot.service

import com.bojogar.bot.entity.User
import com.bojogar.bot.repository.UserRepository
import com.bojogar.bot.util.PhoneUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(private val userRepository: UserRepository) {

    companion object {
        private val log = LoggerFactory.getLogger(UserService::class.java)
    }

    @Transactional
    fun findOrCreate(phone: String, name: String): User {
        val normalized = PhoneUtils.normalizePhone(phone)
        val existing = userRepository.findByPhone(normalized)
        if (existing != null) {
            if (name.isNotBlank() && existing.name != name) {
                existing.name = name
                return userRepository.save(existing)
            }
            return existing
        }

        val displayName = name.ifBlank { normalized }
        log.info("Creating new user: {} ({})", displayName, normalized)
        return userRepository.save(User(phone = normalized, name = displayName))
    }

    fun findByPhone(phone: String): User? {
        return userRepository.findByPhone(PhoneUtils.normalizePhone(phone))
    }

    @Transactional
    fun updateName(phone: String, name: String): User {
        val user = userRepository.findByPhone(PhoneUtils.normalizePhone(phone))
            ?: throw IllegalArgumentException("User not found: $phone")
        user.name = name
        return userRepository.save(user)
    }
}
