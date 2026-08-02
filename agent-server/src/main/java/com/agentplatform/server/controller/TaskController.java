package com.agentplatform.server.controller;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.common.model.AgentTask;
import com.agentplatform.common.model.TaskCreateRequest;
import com.agentplatform.common.model.TaskLogEntry;
import com.agentplatform.core.task.TaskLogBroker;
import com.agentplatform.core.task.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * REST task management endpoints plus an SSE stream for task logs.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskLogBroker taskLogBroker;

    public TaskController(TaskService taskService, TaskLogBroker taskLogBroker) {
        this.taskService = taskService;
        this.taskLogBroker = taskLogBroker;
    }

    @PostMapping
    public ApiResponse<AgentTask> createTask(@Valid @RequestBody TaskCreateRequest request) {
        AgentTask task = taskService.createTask(request);
        if (request.shouldAutoRun()) {
            taskService.runTask(task.getTaskId(), true);
        }
        return ApiResponse.success("task created", task);
    }

    @GetMapping
    public ApiResponse<List<AgentTask>> listTasks() {
        return ApiResponse.success(taskService.listTasks());
    }

    @GetMapping("/{taskId}")
    public ApiResponse<AgentTask> getTask(@PathVariable String taskId) {
        return ApiResponse.success(taskService.getTask(taskId));
    }

    @PostMapping("/{taskId}/run")
    public ApiResponse<AgentTask> runTask(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "true") boolean async) {
        return ApiResponse.success(taskService.runTask(taskId, async));
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<AgentTask> cancelTask(@PathVariable String taskId) {
        return ApiResponse.success(taskService.cancelTask(taskId));
    }

    @GetMapping("/{taskId}/logs")
    public ApiResponse<List<TaskLogEntry>> getTaskLogs(@PathVariable String taskId) {
        return ApiResponse.success(taskService.getTaskLogs(taskId));
    }

    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskEvents(@PathVariable String taskId) {
        AgentTask task = taskService.getTask(taskId);
        SseEmitter emitter = new SseEmitter(0L);
        taskLogBroker.subscribe(taskId, emitter, task.getStatus());
        return emitter;
    }
}
