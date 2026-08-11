package com.bojogar.bot.mapper

import com.bojogar.bot.dto.response.UserResponse
import com.bojogar.bot.entity.User
import org.springframework.stereotype.Component

@Component
class UserMapper {

    fun toResponse(entity: User): UserResponse = UserResponse(
        id = entity.id!!,
        phone = entity.phone,
        name = entity.name,
        createdAt = entity.criadoEm
    )
}
