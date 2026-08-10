package com.bojogar.bot.repository

import com.bojogar.bot.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {

    fun findByPhone(phone: String): User?
}
