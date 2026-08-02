package com.agentplatform.codereview.model;

import java.util.List;

/**
 * Static analysis result for one Java source file.
 */
public record JavaFileSnapshot(
        String relativePath,
        String packageName,
        String className,
        int lineCount,
        int methodCount,
        int fieldCount,
        int importCount,
        int commentCount,
        int todoCount,
        int complexity,
        List<CodeReviewIssue> issues) {

    public JavaFileSnapshot {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
