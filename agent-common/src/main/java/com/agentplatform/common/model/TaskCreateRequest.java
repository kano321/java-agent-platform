package com.agentplatform.common.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request used to create an agent task.
 */
public record TaskCreateRequest(
        @NotBlank(message = "agentId is required") String agentId,
        @NotBlank(message = "input is required") String input,
        Map<String, Object> metadata,
        Boolean autoRun) {

    public TaskCreateRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        autoRun = autoRun == null ? Boolean.TRUE : autoRun;
    }

    public boolean shouldAutoRun() {
        return Boolean.TRUE.equals(autoRun);
    }
}
