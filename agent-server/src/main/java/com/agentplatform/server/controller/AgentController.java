package com.agentplatform.server.controller;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.common.model.AgentInfo;
import com.agentplatform.common.model.AgentRegistrationRequest;
import com.agentplatform.core.registry.AgentRegistry;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for agent registration, unregistration and heartbeat.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentRegistry agentRegistry;

    public AgentController(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @GetMapping
    public ApiResponse<List<AgentInfo>> listAgents() {
        return ApiResponse.success(agentRegistry.listAgentInfos());
    }

    @GetMapping("/{agentId}")
    public ApiResponse<AgentInfo> getAgent(@PathVariable String agentId) {
        return ApiResponse.success(agentRegistry.getAgentInfo(agentId));
    }

    @PostMapping("/register")
    public ApiResponse<AgentInfo> register(@Valid @RequestBody AgentRegistrationRequest request) {
        return ApiResponse.success("agent registered", agentRegistry.registerRemote(request));
    }

    @PostMapping("/{agentId}/heartbeat")
    public ApiResponse<AgentInfo> heartbeat(@PathVariable String agentId) {
        return ApiResponse.success(agentRegistry.heartbeat(agentId));
    }

    @DeleteMapping("/{agentId}")
    public ApiResponse<Void> unregister(@PathVariable String agentId) {
        agentRegistry.unregister(agentId);
        return ApiResponse.success("agent unregistered", null);
    }
}
