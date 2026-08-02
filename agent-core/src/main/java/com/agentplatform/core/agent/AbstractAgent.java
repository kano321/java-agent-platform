package com.agentplatform.core.agent;

import com.agentplatform.common.model.AgentKind;

import java.util.List;

/**
 * Base class that stores immutable agent metadata.
 */
public abstract class AbstractAgent implements Agent {

    private final String agentId;
    private final String name;
    private final String description;
    private final AgentKind kind;
    private final String version;
    private final List<String> tags;

    protected AbstractAgent(
            String agentId,
            String name,
            String description,
            AgentKind kind,
            String version,
            List<String> tags) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.kind = kind;
        this.version = version;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public AgentKind kind() {
        return kind;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public List<String> tags() {
        return tags;
    }
}
