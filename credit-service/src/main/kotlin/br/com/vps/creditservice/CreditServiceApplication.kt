package br.com.vps.creditservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
class CreditServiceApplication

fun main(args: Array<String>) {
    runApplication<CreditServiceApplication>(*args)
}
