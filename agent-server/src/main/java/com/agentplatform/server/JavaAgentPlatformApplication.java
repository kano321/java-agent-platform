package com.agentplatform.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Spring Boot entry point.
 */
@SpringBootApplication(scanBasePackages = "com.agentplatform")
@ConfigurationPropertiesScan(basePackages = "com.agentplatform")
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.agentplatform")
@EntityScan(basePackages = "com.agentplatform")
@EnableTransactionManagement
public class JavaAgentPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaAgentPlatformApplication.class, args);
    }
}
