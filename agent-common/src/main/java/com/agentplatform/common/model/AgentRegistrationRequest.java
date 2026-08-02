package com.agentplatform.common.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request used to register an agent through REST API.
 */
public record AgentRegistrationRequest(
        @NotBlank(message = "agentId is required") String agentId,
        @NotBlank(message = "name is required") String name,
        String description,
        AgentKind kind,
        String version,
        List<String> tags,
        String executionEndpoint) {

    public AgentRegistrationRequest {
        description = description == null ? "" : description;
        kind = kind == null ? AgentKind.REMOTE : kind;
        version = version == null ? "1.0.0" : version;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
