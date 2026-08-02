package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable thread pool settings for the agent task executor.
 */
@ConfigurationProperties(prefix = "app.task")
public record AgentTaskProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        String threadNamePrefix) {

    public AgentTaskProperties {
        if (corePoolSize <= 0) {
            corePoolSize = 4;
        }
        if (maxPoolSize <= 0) {
            maxPoolSize = 16;
        }
        if (queueCapacity <= 0) {
            queueCapacity = 200;
        }
        if (threadNamePrefix == null || threadNamePrefix.isBlank()) {
            threadNamePrefix = "agent-task-";
        }
    }
}
