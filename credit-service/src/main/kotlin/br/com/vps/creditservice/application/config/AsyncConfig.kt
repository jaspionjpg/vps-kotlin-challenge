package br.com.vps.creditservice.application.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableRetry
class AsyncConfig {
    @Bean
    fun notificationExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 5
            queueCapacity = 100
            setThreadNamePrefix("sns-pub-")
            initialize()
        }
}
