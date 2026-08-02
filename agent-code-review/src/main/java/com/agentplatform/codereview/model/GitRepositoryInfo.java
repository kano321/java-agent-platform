package com.agentplatform.codereview.model;

import java.util.List;

/**
 * Snapshot of a local Git repository used as review input.
 */
public record GitRepositoryInfo(
        String repoPath,
        String branch,
        String headCommit,
        String diffBase,
        List<String> javaFilePaths,
        int totalTrackedJavaFiles) {

    public GitRepositoryInfo {
        javaFilePaths = javaFilePaths == null ? List.of() : List.copyOf(javaFilePaths);
    }
}
