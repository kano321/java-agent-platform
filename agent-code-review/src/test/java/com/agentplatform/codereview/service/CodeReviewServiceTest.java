package com.agentplatform.codereview.service;

import com.agentplatform.codereview.config.CodeReviewProperties;
import com.agentplatform.codereview.model.CodeReviewReport;
import com.agentplatform.codereview.model.ReviewCreateRequest;
import com.agentplatform.codereview.store.ReviewRecordStore;
import com.agentplatform.codereview.rag.ReviewRagService;
import com.agentplatform.codereview.util.GitRepositoryScanner;
import com.agentplatform.codereview.util.JavaSourceAnalyzer;
import dev.langchain4j.model.chat.ChatModel;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeReviewServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesAndStoresMarkdownReportWithoutLlm() throws Exception {
        Path sample = Path.of("src/test/resources/sample-java-project").toAbsolutePath();
        copyTree(sample, tempDir);

        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("init")
                    .setAuthor("test", "test@example.com")
                    .setCommitter("test", "test@example.com")
                    .call();
        }

        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        ReviewRagService ragService = mock(ReviewRagService.class);

        CodeReviewService service = new CodeReviewService(
                new JavaSourceAnalyzer(),
                new GitRepositoryScanner(),
                new MarkdownReportGenerator(),
                new ReviewRecordStore(new CodeReviewProperties(tempDir.resolve("reports").toString(), 100)),
                provider,
                ragService);

        CodeReviewReport report = service.runReview(
                new ReviewCreateRequest(tempDir.toString(), null, 100, "general", null),
                "task_1");

        assertThat(report.getReportId()).startsWith("review_");
        assertThat(report.getAnalyzedFileCount()).isGreaterThanOrEqualTo(2);
        assertThat(report.getIssueCount()).isGreaterThanOrEqualTo(4);
        assertThat(report.getMarkdown()).contains("# Java Code Review Report");
        assertThat(report.getMarkdown()).contains("OrderService.java");
        assertThat(Files.exists(tempDir.resolve("reports").resolve(report.getReportId() + ".md"))).isTrue();
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
