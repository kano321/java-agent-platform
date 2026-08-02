package com.agentplatform.core.task;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Execution context handed to an agent. It carries the task input and a
 * cooperative cancellation flag.
 */
public class TaskExecutionContext {

    private final String taskId;
    private final String input;
    private final Map<String, Object> metadata;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public TaskExecutionContext(String taskId, String input, Map<String, Object> metadata) {
        this.taskId = taskId;
        this.input = input;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String getTaskId() {
        return taskId;
    }

    public String getInput() {
        return input;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void requestCancellation() {
        cancelled.set(true);
    }
}
