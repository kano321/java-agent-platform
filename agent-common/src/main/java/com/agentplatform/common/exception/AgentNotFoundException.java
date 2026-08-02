package com.agentplatform.common.exception;

/**
 * Thrown when an agent id does not exist in the registry.
 */
public class AgentNotFoundException extends AgentException {

    public AgentNotFoundException(String agentId) {
        super("Agent not found: " + agentId);
    }
}
