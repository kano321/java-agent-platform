package com.agentplatform.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CodeReviewIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @Test
    void codeReviewTaskGeneratesAndPersistsMarkdownReport() throws Exception {
        Path sample = Path.of("../agent-code-review/src/test/resources/sample-java-project").toAbsolutePath();
        copyTree(sample, tempDir);
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("init")
                    .setAuthor("test", "test@example.com")
                    .setCommitter("test", "test@example.com")
                    .call();
        }

        Map<String, Object> body = Map.of(
                "repoPath", tempDir.toString(),
                "maxFiles", 100);
        ResponseEntity<String> created =
                restTemplate.postForEntity("/api/v1/reviews", body, String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        String taskId = objectMapper.readTree(created.getBody())
                .path("data").path("taskId").asText();
        assertThat(taskId).isNotBlank();

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    ResponseEntity<String> detail =
                            restTemplate.getForEntity("/api/v1/tasks/" + taskId, String.class);
                    JsonNode node = objectMapper.readTree(detail.getBody());
                    assertThat(node.path("data").path("status").asText()).isEqualTo("SUCCEEDED");
                });

        ResponseEntity<String> list =
                restTemplate.getForEntity("/api/v1/reviews?taskId=" + taskId, String.class);
        JsonNode report = objectMapper.readTree(list.getBody()).path("data").get(0);
        String reportId = report.path("reportId").asText();
        assertThat(reportId).isNotBlank();
        assertThat(report.path("analyzedFileCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(report.path("issueCount").asInt()).isGreaterThanOrEqualTo(4);

        ResponseEntity<String> markdown = restTemplate.getForEntity(
                "/api/v1/reviews/" + reportId + "/markdown", String.class);
        assertThat(markdown.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(markdown.getBody()).contains("# Java Code Review Report");
        assertThat(markdown.getBody()).contains("OrderService.java");

        ResponseEntity<String> ragSearch = restTemplate.getForEntity(
                "/api/v1/rag/search?query=OrderService&limit=5", String.class);
        JsonNode ragNode = objectMapper.readTree(ragSearch.getBody());
        assertThat(ragNode.path("data").size()).isGreaterThanOrEqualTo(1);
    }

    private void copyTree(Path source, Path target) throws Exception {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
    }
}
