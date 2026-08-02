package com.agentplatform.common.exception;

/**
 * Thrown when an agent cannot be executed.
 */
public class AgentExecutionException extends AgentException {

    public AgentExecutionException(String message) {
        super(message);
    }

    public AgentExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
