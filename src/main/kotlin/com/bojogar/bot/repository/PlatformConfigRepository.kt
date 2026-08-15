package com.bojogar.bot.repository

import com.bojogar.bot.entity.PlatformConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlatformConfigRepository : JpaRepository<PlatformConfig, String>
