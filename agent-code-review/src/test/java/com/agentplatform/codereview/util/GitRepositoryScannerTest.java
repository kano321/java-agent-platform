package com.agentplatform.codereview.util;

import com.agentplatform.codereview.model.GitRepositoryInfo;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitRepositoryScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scansTrackedJavaFilesFromGitHead() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(source);
        Files.writeString(source.resolve("Hello.java"), "package com.example; public class Hello {}");

        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("init")
                    .setAuthor("test", "test@example.com")
                    .setCommitter("test", "test@example.com")
                    .call();
        }

        GitRepositoryInfo info = new GitRepositoryScanner().scan(tempDir, null, 100);

        assertThat(info.branch()).isNotBlank();
        assertThat(info.headCommit()).isNotBlank();
        assertThat(info.javaFilePaths())
                .contains("src/main/java/com/example/Hello.java");
        assertThat(info.totalTrackedJavaFiles()).isEqualTo(1);
    }

    @Test
    void scansSubdirectoryInsideParentGitRepository() throws Exception {
        Path repo = tempDir.resolve("repo");
        Path sub = repo.resolve("sub/src");
        Files.createDirectories(sub.resolve("main/java/com/example"));
        Files.writeString(sub.resolve("main/java/com/example/Sub.java"),
                "package com.example; public class Sub {}");

        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("init")
                    .setAuthor("test", "test@example.com")
                    .setCommitter("test", "test@example.com")
                    .call();
        }

        GitRepositoryInfo info = new GitRepositoryScanner().scan(sub, null, 100);

        assertThat(info.javaFilePaths())
                .contains("main/java/com/example/Sub.java");
        assertThat(info.totalTrackedJavaFiles()).isEqualTo(1);
    }
}
