package com.agentplatform.codereview.model;

import jakarta.validation.constraints.NotBlank;

/**
 * REST request for triggering a Java code review task.
 */
public record ReviewCreateRequest(
        @NotBlank(message = "repoPath is required") String repoPath,
        String diffBase,
        Integer maxFiles,
        String focus,
        String agentId) {

    public ReviewCreateRequest {
        maxFiles = maxFiles == null || maxFiles <= 0 ? 200 : maxFiles;
        focus = focus == null || focus.isBlank() ? "general" : focus;
        agentId = agentId == null || agentId.isBlank() ? "code_review_agent" : agentId;
    }
}
