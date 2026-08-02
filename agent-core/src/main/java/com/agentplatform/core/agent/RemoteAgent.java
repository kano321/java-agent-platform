package com.agentplatform.core.agent;

import com.agentplatform.common.exception.AgentExecutionException;
import com.agentplatform.common.model.AgentKind;
import com.agentplatform.common.model.AgentRegistrationRequest;
import com.agentplatform.core.task.TaskExecutionContext;
import com.agentplatform.core.task.TaskLogSink;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Adapter that delegates execution to a remote HTTP endpoint. It is used for
 * agents registered through the REST registration API.
 */
public class RemoteAgent extends AbstractAgent {

    private final String executionEndpoint;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RemoteAgent(AgentRegistrationRequest request) {
        super(
                request.agentId(),
                request.name(),
                request.description(),
                request.kind(),
                request.version(),
                request.tags());
        this.executionEndpoint = request.executionEndpoint();
    }

    @Override
    public String execute(TaskExecutionContext context, TaskLogSink logSink) {
        if (executionEndpoint == null || executionEndpoint.isBlank()) {
            throw new AgentExecutionException(
                    "Remote agent " + agentId() + " has no executionEndpoint");
        }

        try {
            logSink.info("Forwarding task to remote endpoint: " + executionEndpoint);
            String body = objectMapper.writeValueAsString(Map.of(
                    "taskId", context.getTaskId(),
                    "input", context.getInput(),
                    "metadata", context.getMetadata()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(executionEndpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new AgentExecutionException(
                        "Remote agent returned HTTP " + response.statusCode() + ": " + response.body());
            }
            logSink.info("Remote agent responded with " + response.statusCode());
            return response.body();
        } catch (AgentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentExecutionException("Remote agent call failed: " + e.getMessage(), e);
        }
    }
}
