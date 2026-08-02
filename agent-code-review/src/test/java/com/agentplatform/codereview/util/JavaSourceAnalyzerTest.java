package com.agentplatform.codereview.util;

import com.agentplatform.codereview.model.CodeReviewIssue;
import com.agentplatform.codereview.model.JavaFileSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSourceAnalyzerTest {

    private final JavaSourceAnalyzer analyzer = new JavaSourceAnalyzer();

    @Test
    void detectsCommonJavaIssues() {
        Path root = Path.of("src/test/resources/sample-java-project").toAbsolutePath();
        Path file = root.resolve("src/main/java/com/example/OrderService.java");

        JavaFileSnapshot snapshot = analyzer.analyze(root, file);

        assertThat(snapshot.lineCount()).isGreaterThan(0);
        assertThat(snapshot.className()).isEqualTo("OrderService");
        assertThat(snapshot.methodCount()).isGreaterThanOrEqualTo(2);

        List<String> rules = snapshot.issues().stream()
                .map(CodeReviewIssue::rule)
                .toList();
        assertThat(rules).contains(
                "EMPTY_CATCH",
                "PRINT_STACK_TRACE",
                "SYSTEM_OUT",
                "THREAD_SLEEP",
                "TODO_COMMENT");
    }
}
