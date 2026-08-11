package com.bojogar.bot.controller

import com.bojogar.bot.dto.response.UserResponse
import com.bojogar.bot.exception.ResourceNotFoundException
import com.bojogar.bot.service.UserService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/api/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/me")
    fun getMe(@RequestHeader("X-User-Phone") phone: String): UserResponse {
        return userService.findByPhone(phone)
            ?: throw ResourceNotFoundException("Usuário não encontrado")
    }

    @PatchMapping("/me/name")
    fun updateName(
        @RequestHeader("X-User-Phone") phone: String,
        @RequestBody body: Map<String, String>
    ): UserResponse {
        val name = body["name"] ?: throw IllegalArgumentException("Nome obrigatório")
        return userService.updateName(phone, name)
    }
}
