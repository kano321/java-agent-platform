package com.agentplatform.common.exception;

/**
 * Thrown when a task id does not exist.
 */
public class TaskNotFoundException extends AgentException {

    public TaskNotFoundException(String taskId) {
        super("Task not found: " + taskId);
    }
}
