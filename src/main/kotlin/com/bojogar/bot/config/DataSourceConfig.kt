package com.bojogar.bot.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty("DATABASE_URL")
class DataSourceConfig(
    @Value("\${DATABASE_URL}")
    private val rawUrl: String
) {

    companion object {
        private val log = LoggerFactory.getLogger(DataSourceConfig::class.java)
    }

    @Bean
    fun dataSource(): DataSource {
        val normalizedUrl = when {
            rawUrl.startsWith("jdbc:postgresql://") ->
                rawUrl.removePrefix("jdbc:")
            rawUrl.startsWith("postgresql://") ->
                rawUrl
            rawUrl.startsWith("postgres://") ->
                "postgresql://${rawUrl.removePrefix("postgres://")}"
            else ->
                error("DATABASE_URL invalida. Esperado postgres://, postgresql:// ou jdbc:postgresql://")
        }

        val uri = URI(normalizedUrl)
        val userInfo = uri.userInfo ?: error("DATABASE_URL nao possui usuario e senha.")
        val credentials = userInfo.split(":", limit = 2)

        val jdbcUrl = buildString {
            append("jdbc:postgresql://")
            append(uri.host)
            if (uri.port != -1) {
                append(":")
                append(uri.port)
            }
            append(uri.path)
            if (!uri.query.isNullOrBlank()) {
                append("?")
                append(uri.query)
            }
        }

        log.info("Conectando ao PostgreSQL: {}", jdbcUrl)

        val hikari = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = credentials[0]
            this.password = credentials.getOrElse(1) { "" }
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 3
            minimumIdle = 1
            connectionTimeout = 30_000
            idleTimeout = 600_000
            maxLifetime = 1_800_000
            keepaliveTime = 60_000
            validationTimeout = 5_000
            isAutoCommit = true
        }

        return HikariDataSource(hikari)
    }
}
