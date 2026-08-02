package com.agentplatform.core.registry;

import com.agentplatform.common.model.AgentInfo;
import com.agentplatform.core.agent.Agent;

/**
 * Internal registry entry binding an executable agent to its metadata.
 */
public record AgentRegistration(Agent agent, AgentInfo info, boolean local) {
}
