package com.agentplatform.codereview.model;

/**
 * A single code review finding.
 */
public record CodeReviewIssue(
        Severity severity,
        String filePath,
        int line,
        String rule,
        String message) {
}
