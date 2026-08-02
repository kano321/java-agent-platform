package com.agentplatform.core.registry;

import com.agentplatform.common.exception.AgentAlreadyExistsException;
import com.agentplatform.common.exception.AgentExecutionException;
import com.agentplatform.common.exception.AgentNotFoundException;
import com.agentplatform.common.model.AgentInfo;
import com.agentplatform.common.model.AgentRegistrationRequest;
import com.agentplatform.common.model.AgentStatus;
import com.agentplatform.core.agent.Agent;
import com.agentplatform.core.agent.RemoteAgent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory agent registry with registration, unregistration,
 * heartbeat and expiry cleanup.
 */
@Component
public class AgentRegistry {

    private final Map<String, AgentRegistration> agents = new ConcurrentHashMap<>();

    public synchronized AgentInfo register(Agent agent) {
        return register(agent, agent.registrationRequest(), true);
    }

    public synchronized AgentInfo register(Agent agent, AgentRegistrationRequest request, boolean local) {
        if (agents.containsKey(request.agentId())) {
            throw new AgentAlreadyExistsException(request.agentId());
        }
        Instant now = Instant.now();
        AgentInfo info = new AgentInfo(
                request.agentId(),
                request.name(),
                request.description(),
                request.kind(),
                request.version(),
                request.tags(),
                AgentStatus.ACTIVE,
                now,
                now,
                now);
        agents.put(request.agentId(), new AgentRegistration(agent, info, local));
        return info;
    }

    public AgentInfo registerRemote(AgentRegistrationRequest request) {
        return register(new RemoteAgent(request), request, false);
    }

    public AgentInfo getAgentInfo(String agentId) {
        AgentRegistration registration = agents.get(agentId);
        if (registration == null) {
            throw new AgentNotFoundException(agentId);
        }
        return registration.info();
    }

    public Agent requireActiveAgent(String agentId) {
        AgentRegistration registration = agents.get(agentId);
        if (registration == null) {
            throw new AgentNotFoundException(agentId);
        }
        if (registration.info().status() != AgentStatus.ACTIVE) {
            throw new AgentExecutionException("Agent is not active: " + agentId);
        }
        return registration.agent();
    }

    public AgentInfo heartbeat(String agentId) {
        return heartbeat(agentId, Instant.now());
    }

    public synchronized AgentInfo heartbeat(String agentId, Instant now) {
        AgentRegistration registration = agents.get(agentId);
        if (registration == null) {
            throw new AgentNotFoundException(agentId);
        }
        AgentInfo updated = registration.info().withHeartbeat(now);
        agents.put(agentId, new AgentRegistration(registration.agent(), updated, registration.local()));
        return updated;
    }

    public void heartbeatLocals(Instant now) {
        for (AgentRegistration registration : agents.values()) {
            if (registration.local()) {
                heartbeat(registration.info().agentId(), now);
            }
        }
    }

    public synchronized AgentInfo unregister(String agentId) {
        AgentRegistration registration = agents.remove(agentId);
        if (registration == null) {
            throw new AgentNotFoundException(agentId);
        }
        return registration.info().withStatus(AgentStatus.OFFLINE, Instant.now());
    }

    public List<AgentInfo> listAgentInfos() {
        return agents.values().stream()
                .map(AgentRegistration::info)
                .sorted(Comparator.comparing(AgentInfo::agentId))
                .toList();
    }

    public boolean contains(String agentId) {
        return agents.containsKey(agentId);
    }

    public synchronized List<String> removeExpired(Duration timeout, Instant now) {
        List<String> removed = new ArrayList<>();
        for (AgentRegistration registration : agents.values()) {
            if (registration.local()) {
                continue;
            }
            Instant lastHeartbeat = registration.info().lastHeartbeatAt();
            if (lastHeartbeat != null && lastHeartbeat.isBefore(now.minus(timeout))) {
                agents.remove(registration.info().agentId());
                removed.add(registration.info().agentId());
            }
        }
        return removed;
    }
}
