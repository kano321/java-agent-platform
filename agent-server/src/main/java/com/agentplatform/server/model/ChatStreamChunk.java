package com.agentplatform.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One SSE chunk consumed by the dashboard streaming chat parser.
 */
public record ChatStreamChunk(
        int code,
        String result,
        @JsonProperty("is_end") boolean isEnd) {
}
