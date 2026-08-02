package com.agentplatform.common.model;

import java.time.Instant;
import java.util.Map;

/**
 * Mutable task record stored in memory. Status transitions are synchronized
 * because a task can be updated by both the async worker and the REST thread.
 */
public class AgentTask {

    private final String taskId;
    private final String agentId;
    private final String input;
    private final Map<String, Object> metadata;

    private volatile TaskStatus status;
    private volatile String output;
    private volatile String error;
    private volatile int attemptCount;
    private volatile Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile Instant updatedAt;

    public AgentTask(String taskId, String agentId, String input, Map<String, Object> metadata) {
        this.taskId = taskId;
        this.agentId = agentId;
        this.input = input;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.status = TaskStatus.PENDING;
        this.attemptCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public synchronized boolean compareAndSetStatus(TaskStatus expected, TaskStatus next) {
        if (this.status != expected) {
            return false;
        }
        this.status = next;
        this.updatedAt = Instant.now();
        if (next == TaskStatus.RUNNING) {
            this.startedAt = this.updatedAt;
        }
        if (next == TaskStatus.SUCCEEDED || next == TaskStatus.FAILED || next == TaskStatus.CANCELED) {
            this.finishedAt = this.updatedAt;
        }
        return true;
    }

    public synchronized void incrementAttemptCount() {
        this.attemptCount++;
        this.updatedAt = Instant.now();
    }

    public synchronized void restoreState(
            TaskStatus status,
            int attemptCount,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            Instant updatedAt) {
        this.status = status;
        this.attemptCount = attemptCount;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getInput() {
        return input;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
