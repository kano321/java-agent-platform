package com.agentplatform.common.exception;

/**
 * Thrown when an agent id is already registered.
 */
public class AgentAlreadyExistsException extends AgentException {

    public AgentAlreadyExistsException(String agentId) {
        super("Agent already registered: " + agentId);
    }
}
