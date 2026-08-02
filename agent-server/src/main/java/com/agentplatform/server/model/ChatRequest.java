package com.agentplatform.server.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request body shared by the normal and SSE chat endpoints. Field names match
 * the request built by the platform dashboard chat component.
 */
public record ChatRequest(
        @NotBlank(message = "query is required") String query,
        String userId,
        String appName,
        Boolean isStream,
        Boolean isThinkMode,
        String imgBase64,
        List<ChatImageFile> imgFiles,
        List<ChatHistoryMessage> history,
        Map<String, Object> contextData,
        Boolean hasContext,
        Map<String, Object> metadata) {

    public ChatRequest {
        query = query == null ? "" : query.trim();
        history = history == null
                ? List.of()
                : history.stream().filter(Objects::nonNull).toList();
        metadata = metadata == null ? Map.of() : metadata;
    }

    public record ChatHistoryMessage(String role, String content) {
    }

    public record ChatImageFile(String name, String type, Integer size, String base64) {
    }
}
