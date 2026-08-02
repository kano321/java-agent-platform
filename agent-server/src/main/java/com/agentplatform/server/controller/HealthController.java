package com.agentplatform.server.controller;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.core.registry.AgentRegistry;
import com.agentplatform.core.task.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight health endpoint used by local verification and Docker health checks.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final AgentRegistry agentRegistry;
    private final TaskService taskService;

    public HealthController(AgentRegistry agentRegistry, TaskService taskService) {
        this.agentRegistry = agentRegistry;
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = Map.of(
                "status", "UP",
                "service", "java-agent-platform",
                "registeredAgents", agentRegistry.listAgentInfos().size(),
                "tasks", taskService.listTasks().size());
        return ApiResponse.success(data);
    }
}
