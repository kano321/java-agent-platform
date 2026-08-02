package com.agentplatform.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JavaAgentPlatformIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void healthAndAgentsAreAvailable() throws Exception {
        ResponseEntity<String> health = restTemplate.getForEntity("/api/v1/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode healthBody = objectMapper.readTree(health.getBody());
        assertThat(healthBody.path("data").path("status").asText()).isEqualTo("UP");

        ResponseEntity<String> agents = restTemplate.getForEntity("/api/v1/agents", String.class);
        JsonNode agentsBody = objectMapper.readTree(agents.getBody());
        assertThat(agentsBody.path("data").toString()).contains("demo_agent");
    }

    @Test
    void createRunTaskAndReadLogs() throws Exception {
        Map<String, Object> request = Map.of(
                "agentId", "demo_agent",
                "input", "verify task pipeline",
                "autoRun", true);
        ResponseEntity<String> created = restTemplate.postForEntity("/api/v1/tasks", request, String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode createdNode = objectMapper.readTree(created.getBody());
        String taskId = createdNode.path("data").path("taskId").asText();
        assertThat(taskId).isNotBlank();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    ResponseEntity<String> detail =
                            restTemplate.getForEntity("/api/v1/tasks/" + taskId, String.class);
                    JsonNode detailNode = objectMapper.readTree(detail.getBody());
                    assertThat(detailNode.path("data").path("status").asText()).isEqualTo("SUCCEEDED");
                });

        ResponseEntity<String> logs =
                restTemplate.getForEntity("/api/v1/tasks/" + taskId + "/logs", String.class);
        JsonNode logsNode = objectMapper.readTree(logs.getBody());
        assertThat(logsNode.path("data").toString()).contains("Demo agent started");

        ResponseEntity<String> deleted = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.DELETE, null, String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> afterDelete =
                restTemplate.getForEntity("/api/v1/tasks/" + taskId, String.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void registerHeartbeatAndUnregisterRemoteAgent() {
        Map<String, Object> request = Map.of(
                "agentId", "remote_test",
                "name", "Remote Test",
                "kind", "REMOTE",
                "version", "1.0.0",
                "tags", List.of("test"),
                "executionEndpoint", "http://localhost:9999/execute");
        ResponseEntity<String> registered =
                restTemplate.postForEntity("/api/v1/agents/register", request, String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registered.getBody()).contains("remote_test");

        ResponseEntity<String> heartbeat =
                restTemplate.postForEntity("/api/v1/agents/remote_test/heartbeat", null, String.class);
        assertThat(heartbeat.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> deleted = restTemplate.exchange(
                "/api/v1/agents/remote_test", HttpMethod.DELETE, null, String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void sseStreamPushesTaskLogs() throws Exception {
        Map<String, Object> request = Map.of(
                "agentId", "demo_agent",
                "input", "sse stream check",
                "autoRun", false);
        ResponseEntity<String> created = restTemplate.postForEntity("/api/v1/tasks", request, String.class);
        String taskId = objectMapper.readTree(created.getBody())
                .path("data").path("taskId").asText();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest streamRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/tasks/" + taskId + "/events"))
                .GET()
                .build();

        CompletableFuture<Stream<String>> bodyFuture = client.sendAsync(
                        streamRequest, HttpResponse.BodyHandlers.ofLines())
                .thenApply(HttpResponse::body);
        Stream<String> lines = bodyFuture.get(5, TimeUnit.SECONDS);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<String> eventsFuture = CompletableFuture.supplyAsync(() -> {
                StringBuilder events = new StringBuilder();
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    events.append(line).append('\n');
                    if (line.startsWith("data:") && line.contains("SUCCEEDED")) {
                        break;
                    }
                }
                return events.toString();
            }, executor);

            restTemplate.postForEntity(
                    "/api/v1/tasks/" + taskId + "/run?async=true",
                    HttpEntity.EMPTY,
                    String.class);

            String events = eventsFuture.get(15, TimeUnit.SECONDS);
            assertThat(events).contains("event:connected");
            assertThat(events).contains("event:status");
            assertThat(events).contains("event:log");
        } finally {
            lines.close();
            executor.shutdownNow();
        }
    }
}
