package com.agentplatform.core.task;

import com.agentplatform.common.exception.AgentExecutionException;
import com.agentplatform.common.exception.TaskNotFoundException;
import com.agentplatform.common.model.AgentInfo;
import com.agentplatform.common.model.AgentStatus;
import com.agentplatform.common.model.AgentTask;
import com.agentplatform.common.model.TaskCreateRequest;
import com.agentplatform.common.model.TaskLogEntry;
import com.agentplatform.common.model.TaskLogLevel;
import com.agentplatform.common.model.TaskStatus;
import com.agentplatform.common.model.TaskStatusEvent;
import com.agentplatform.core.agent.Agent;
import com.agentplatform.core.persistence.TaskEntity;
import com.agentplatform.core.persistence.TaskRepository;
import com.agentplatform.core.registry.AgentRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates task creation, async execution, cancellation and log retrieval.
 */
@Service
public class TaskService {

    private final AgentRegistry agentRegistry;
    private final TaskLogBroker logBroker;
    private final Executor taskExecutor;
    private final TaskRepository taskRepository;
    private final Map<String, AgentTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, TaskExecutionContext> runningContexts = new ConcurrentHashMap<>();
    private final AtomicLong taskIdSequence = new AtomicLong();

    public TaskService(
            AgentRegistry agentRegistry,
            TaskLogBroker logBroker,
            @Qualifier("agentTaskExecutor") Executor taskExecutor,
            TaskRepository taskRepository) {
        this.agentRegistry = agentRegistry;
        this.logBroker = logBroker;
        this.taskExecutor = taskExecutor;
        this.taskRepository = taskRepository;
    }

    public AgentTask createTask(TaskCreateRequest request) {
        AgentInfo info = agentRegistry.getAgentInfo(request.agentId());
        if (info.status() != AgentStatus.ACTIVE) {
            throw new AgentExecutionException("Agent is not active: " + request.agentId());
        }

        String taskId = "task_" + System.currentTimeMillis() + "_" + taskIdSequence.incrementAndGet();
        AgentTask task = new AgentTask(taskId, request.agentId(), request.input(), request.metadata());
        tasks.put(taskId, task);
        taskRepository.save(toEntity(task));
        logBroker.append(taskId, TaskLogLevel.INFO, "Task created, agent=" + request.agentId());
        publishStatus(task);
        return task;
    }

    public AgentTask runTask(String taskId, boolean async) {
        AgentTask task = requireTask(taskId);
        if (!task.compareAndSetStatus(TaskStatus.PENDING, TaskStatus.RUNNING)) {
            throw new IllegalStateException(
                    "Task can only be started from PENDING, current=" + task.getStatus());
        }
        taskRepository.save(toEntity(task));
        logBroker.append(taskId, TaskLogLevel.INFO, "Task submitted to async executor");
        publishStatus(task);
        if (async) {
            taskExecutor.execute(() -> executeInternal(taskId));
        } else {
            executeInternal(taskId);
        }
        return task;
    }

    private void executeInternal(String taskId) {
        AgentTask task = requireTask(taskId);
        TaskExecutionContext context = new TaskExecutionContext(
                taskId, task.getInput(), task.getMetadata());
        runningContexts.put(taskId, context);
        BrokerTaskLogSink logSink = new BrokerTaskLogSink(logBroker, taskId);

        try {
            task.incrementAttemptCount();
            Agent agent = agentRegistry.requireActiveAgent(task.getAgentId());
            logSink.info("Agent " + agent.agentId() + " started");
            String output = agent.execute(context, logSink);
            if (context.isCancelled()) {
                task.setOutput(output);
                task.setError("cancelled by user");
                task.compareAndSetStatus(TaskStatus.RUNNING, TaskStatus.CANCELED);
                logSink.warn("Task cancelled");
            } else {
                task.setOutput(output);
                task.compareAndSetStatus(TaskStatus.RUNNING, TaskStatus.SUCCEEDED);
                logSink.info("Task succeeded");
            }
        } catch (Exception e) {
            task.setError(e.getMessage());
            task.compareAndSetStatus(TaskStatus.RUNNING, TaskStatus.FAILED);
            logSink.error("Task failed: " + e.getMessage());
        } finally {
            runningContexts.remove(taskId);
            taskRepository.save(toEntity(task));
            publishStatus(task);
            if (isTerminal(task.getStatus())) {
                logBroker.complete(taskId);
            }
        }
    }

    public AgentTask cancelTask(String taskId) {
        AgentTask task = requireTask(taskId);
        if (task.getStatus() == TaskStatus.PENDING) {
            task.compareAndSetStatus(TaskStatus.PENDING, TaskStatus.CANCELED);
            taskRepository.save(toEntity(task));
            logBroker.append(taskId, TaskLogLevel.WARN, "Task cancelled before execution");
            publishStatus(task);
            logBroker.complete(taskId);
        } else if (task.getStatus() == TaskStatus.RUNNING) {
            TaskExecutionContext context = runningContexts.get(taskId);
            if (context != null) {
                context.requestCancellation();
            }
            logBroker.append(taskId, TaskLogLevel.WARN, "Cancellation requested");
        } else {
            throw new IllegalStateException(
                    "Task cannot be cancelled from status " + task.getStatus());
        }
        return task;
    }

    public AgentTask getTask(String taskId) {
        AgentTask task = tasks.get(taskId);
        if (task == null) {
            TaskEntity entity = taskRepository.findById(taskId)
                    .orElseThrow(() -> new TaskNotFoundException(taskId));
            task = fromEntity(entity);
            tasks.put(taskId, task);
        }
        return task;
    }

    public List<AgentTask> listTasks() {
        if (tasks.isEmpty()) {
            taskRepository.findAll().forEach(entity -> tasks.put(entity.getTaskId(), fromEntity(entity)));
        }
        return tasks.values().stream()
                .sorted(Comparator.comparing(AgentTask::getCreatedAt).reversed())
                .toList();
    }

    public List<TaskLogEntry> getTaskLogs(String taskId) {
        requireTask(taskId);
        return logBroker.getLogs(taskId);
    }

    public void deleteTask(String taskId) {
        AgentTask task = getTask(taskId);
        if (task.getStatus() == TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Task is running and cannot be deleted: " + taskId);
        }
        tasks.remove(taskId);
        taskRepository.deleteById(taskId);
        logBroker.delete(taskId);
    }

    private AgentTask requireTask(String taskId) {
        AgentTask task = tasks.get(taskId);
        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }
        return task;
    }

    private void publishStatus(AgentTask task) {
        logBroker.publishStatus(new TaskStatusEvent(task.getTaskId(), task.getStatus(), Instant.now()));
    }

    private TaskEntity toEntity(AgentTask task) {
        TaskEntity entity = new TaskEntity();
        entity.setTaskId(task.getTaskId());
        entity.setAgentId(task.getAgentId());
        entity.setInput(task.getInput());
        entity.setStatus(task.getStatus().name());
        entity.setOutput(task.getOutput());
        entity.setErrorMessage(task.getError());
        entity.setAttemptCount(task.getAttemptCount());
        entity.setCreatedAt(task.getCreatedAt());
        entity.setStartedAt(task.getStartedAt());
        entity.setFinishedAt(task.getFinishedAt());
        entity.setUpdatedAt(task.getUpdatedAt());
        return entity;
    }

    private AgentTask fromEntity(TaskEntity entity) {
        AgentTask task = new AgentTask(entity.getTaskId(), entity.getAgentId(), entity.getInput(), Map.of());
        task.setOutput(entity.getOutput());
        task.setError(entity.getErrorMessage());
        task.restoreState(
                TaskStatus.valueOf(entity.getStatus()),
                entity.getAttemptCount() == null ? 0 : entity.getAttemptCount(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getUpdatedAt());
        return task;
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.SUCCEEDED
                || status == TaskStatus.FAILED
                || status == TaskStatus.CANCELED;
    }
}
