package com.agentplatform.common.model;

import java.time.Instant;

/**
 * Immutable log entry produced during task execution.
 */
public record TaskLogEntry(
        String taskId,
        long sequence,
        TaskLogLevel level,
        String message,
        Instant timestamp) {
}
