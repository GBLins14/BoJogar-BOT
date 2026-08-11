package com.bojogar.bot.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthController(
    private val dataSource: DataSource
) {

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        val dbStatus = try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").use { it.next() }
                }
            }
            "up"
        } catch (e: Exception) {
            "down"
        }

        val status = if (dbStatus == "up") "ok" else "degraded"
        return ResponseEntity.ok(
            mapOf("status" to status, "database" to dbStatus)
        )
    }
}
