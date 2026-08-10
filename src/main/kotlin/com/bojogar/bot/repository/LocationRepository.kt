package com.bojogar.bot.repository

import com.bojogar.bot.entity.Location
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LocationRepository : JpaRepository<Location, UUID> {

    fun findByNameContainingIgnoreCase(name: String): List<Location>
}
