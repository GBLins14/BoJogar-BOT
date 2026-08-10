package com.bojogar.bot.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty("database.url")
class DataSourceConfig {

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

        return HikariDataSource(config)
    }
}
