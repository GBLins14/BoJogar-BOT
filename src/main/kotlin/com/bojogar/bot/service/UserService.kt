package com.bojogar.bot.service

import com.bojogar.bot.dto.response.UserResponse
import com.bojogar.bot.entity.User
import com.bojogar.bot.mapper.UserMapper
import com.bojogar.bot.repository.UserRepository
import com.bojogar.bot.util.PhoneUtils
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val userMapper: UserMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(UserService::class.java)
    }

    @Transactional
    fun findOrCreate(phone: String, name: String): UserResponse {
        val normalized = PhoneUtils.normalizePhone(phone)
        val existing = userRepository.findByPhone(normalized)
        if (existing != null) {
            if (name.isNotBlank() && existing.name != name) {
                existing.name = name
                return userMapper.toResponse(userRepository.save(existing))
            }
            return userMapper.toResponse(existing)
        }

        val displayName = name.ifBlank { normalized }
        log.info("Creating new user: {} ({})", displayName, normalized)
        return try {
            userMapper.toResponse(userRepository.save(User(phone = normalized, name = displayName)))
        } catch (e: DataIntegrityViolationException) {
            // Another thread created the user between our find and insert
            log.debug("Concurrent user creation for {}, fetching existing", normalized)
            val created = userRepository.findByPhone(normalized)
                ?: throw e // Should not happen — rethrow if it does
            userMapper.toResponse(created)
        }
    }

    @Transactional(readOnly = true)
    fun findByPhone(phone: String): UserResponse? {
        val user = userRepository.findByPhone(PhoneUtils.normalizePhone(phone))
        return user?.let { userMapper.toResponse(it) }
    }

    @Transactional
    fun updateName(phone: String, name: String): UserResponse {
        val user = userRepository.findByPhone(PhoneUtils.normalizePhone(phone))
            ?: throw IllegalArgumentException("User not found: $phone")
        user.name = name
        return userMapper.toResponse(userRepository.save(user))
    }
}
