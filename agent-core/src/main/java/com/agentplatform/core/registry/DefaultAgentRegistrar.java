package com.agentplatform.core.registry;

import com.agentplatform.core.agent.Agent;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registers all local Agent beans into the registry when the application starts.
 */
@Component
public class DefaultAgentRegistrar implements InitializingBean {

    private final AgentRegistry registry;
    private final List<Agent> agents;

    public DefaultAgentRegistrar(AgentRegistry registry, List<Agent> agents) {
        this.registry = registry;
        this.agents = agents;
    }

    @Override
    public void afterPropertiesSet() {
        for (Agent agent : agents) {
            registry.register(agent);
        }
    }
}
