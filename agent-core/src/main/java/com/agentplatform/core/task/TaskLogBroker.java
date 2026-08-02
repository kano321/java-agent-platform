package com.agentplatform.core.task;

import com.agentplatform.common.model.TaskLogEntry;
import com.agentplatform.common.model.TaskLogLevel;
import com.agentplatform.common.model.TaskStatus;
import com.agentplatform.common.model.TaskStatusEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory task log store plus SSE fan-out. New subscribers receive a replay
 * of buffered logs and then live events.
 */
@Component
public class TaskLogBroker {

    private final Map<String, CopyOnWriteArrayList<TaskLogEntry>> logs = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public void append(String taskId, TaskLogLevel level, String message) {
        long sequence = sequences.computeIfAbsent(taskId, key -> new AtomicLong()).incrementAndGet();
        TaskLogEntry entry = new TaskLogEntry(taskId, sequence, level, message, Instant.now());
        logs.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(entry);
        broadcast(taskId, "log", entry);
    }

    public void publishStatus(TaskStatusEvent event) {
        broadcast(event.taskId(), "status", event);
    }

    public List<TaskLogEntry> getLogs(String taskId) {
        CopyOnWriteArrayList<TaskLogEntry> entries = logs.get(taskId);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    public void subscribe(String taskId, SseEmitter emitter, TaskStatus currentStatus) {
        CopyOnWriteArrayList<SseEmitter> emitters =
                subscribers.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        emitter.onError(error -> remove(taskId, emitter));

        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name("connected")
                        .data(Map.of("taskId", taskId, "message", "connected")));
                for (TaskLogEntry entry : getLogs(taskId)) {
                    emitter.send(SseEmitter.event().name("log").data(entry));
                }
                if (currentStatus != null) {
                    emitter.send(SseEmitter.event()
                            .name("status")
                            .data(new TaskStatusEvent(taskId, currentStatus, Instant.now())));
                }
                if (isTerminal(currentStatus)) {
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(Map.of("taskId", taskId, "message", "stream completed")));
                    emitter.complete();
                    remove(taskId, emitter);
                }
            }
        } catch (Exception e) {
            remove(taskId, emitter);
        }
    }

    /**
     * Completes all open emitters for a terminal task.
     */
    public void complete(String taskId) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.remove(taskId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(Map.of("taskId", taskId, "message", "stream completed")));
                    emitter.complete();
                }
            } catch (Exception e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // emitter is already closed
                }
            }
        }
    }

    /**
     * Removes buffered logs and closes any open SSE streams for a deleted task.
     */
    public void delete(String taskId) {
        logs.remove(taskId);
        sequences.remove(taskId);
        complete(taskId);
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.SUCCEEDED
                || status == TaskStatus.FAILED
                || status == TaskStatus.CANCELED;
    }

    private void broadcast(String taskId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                }
            } catch (Exception e) {
                remove(taskId, emitter);
            }
        }
    }

    private void remove(String taskId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(taskId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}
