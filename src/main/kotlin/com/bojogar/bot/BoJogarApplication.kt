package com.bojogar.bot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
class BoJogarApplication

fun main(args: Array<String>) {
    runApplication<BoJogarApplication>(*args)
}
