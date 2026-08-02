package com.agentplatform.core.scheduler;

import com.agentplatform.core.registry.AgentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Keeps local agents alive and removes remote agents that stopped heartbeating.
 */
@Component
public class AgentHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentHeartbeatScheduler.class);

    private final AgentRegistry registry;
    private final Duration heartbeatTimeout;

    public AgentHeartbeatScheduler(
            AgentRegistry registry,
            @Value("${app.agent-registry.heartbeat-timeout:60s}") Duration heartbeatTimeout) {
        this.registry = registry;
        this.heartbeatTimeout = heartbeatTimeout;
    }

    @Scheduled(fixedDelayString = "${app.agent-registry.cleanup-interval:30000}")
    public void heartbeatAndCleanup() {
        registry.heartbeatLocals(Instant.now());
        List<String> removed = registry.removeExpired(heartbeatTimeout, Instant.now());
        if (!removed.isEmpty()) {
            log.warn("Removed expired agents: {}", removed);
        }
    }
}
