package com.agentplatform.common.model;

import java.time.Instant;

/**
 * Status change event published to SSE subscribers.
 */
public record TaskStatusEvent(
        String taskId,
        TaskStatus status,
        Instant timestamp) {
}
