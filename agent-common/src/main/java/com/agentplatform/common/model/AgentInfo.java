package com.agentplatform.common.model;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of a registered agent.
 */
public record AgentInfo(
        String agentId,
        String name,
        String description,
        AgentKind kind,
        String version,
        List<String> tags,
        AgentStatus status,
        Instant registeredAt,
        Instant lastHeartbeatAt,
        Instant updatedAt) {

    public AgentInfo {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public AgentInfo withHeartbeat(Instant now) {
        return new AgentInfo(
                agentId, name, description, kind, version, tags,
                AgentStatus.ACTIVE, registeredAt, now, now);
    }

    public AgentInfo withStatus(AgentStatus nextStatus, Instant now) {
        return new AgentInfo(
                agentId, name, description, kind, version, tags,
                nextStatus, registeredAt, lastHeartbeatAt, now);
    }
}
