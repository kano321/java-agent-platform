package com.agentplatform.server.model;

/**
 * Response format for a normal JSON chat request: code 0 plus result.answer.
 */
public record ChatResponse(int code, ChatResult result, String message, String error) {

    public static ChatResponse ok(String answer) {
        return new ChatResponse(0, new ChatResult(answer == null ? "" : answer), "success", null);
    }

    public static ChatResponse error(String message, String error) {
        return new ChatResponse(4, null, message, error);
    }

    public record ChatResult(String answer) {
    }
}
