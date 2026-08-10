package com.bojogar.bot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class BoJogarApplication

fun main(args: Array<String>) {
    runApplication<BoJogarApplication>(*args)
}
