package com.bojogar.bot.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty("database.url")
class DataSourceConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun dataSource(@Value("\${database.url}") databaseUrl: String): DataSource {
        val uri = URI(databaseUrl)
        val userInfo = uri.userInfo.split(":", limit = 2)
        val query = if (uri.query != null) "?${uri.query}" else ""
        val port = if (uri.port != -1) ":${uri.port}" else ""

        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${uri.host}${port}${uri.path}${query}"
            username = userInfo[0]
            password = userInfo.getOrElse(1) { "" }
            maximumPoolSize = 5
        }

        val dataSource = HikariDataSource(config)

        log.info("Executando migrations Flyway...")

        // Limpa baseline que pulou a V1 no deploy anterior
        try {
            dataSource.connection.use { conn ->
                conn.createStatement().execute(
                    "DELETE FROM flyway_schema_history WHERE type = 'BASELINE'"
                )
            }
        } catch (_: Exception) {
            // flyway_schema_history ainda nao existe
        }

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()

        log.info("Migrations Flyway concluidas")

        return dataSource
    }
}
