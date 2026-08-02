package com.agentplatform.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalized LLM configuration. Keys are never hardcoded and the chat model
 * bean is only created when an API key is present.
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        String provider,
        String apiKey,
        String baseUrl,
        String modelName,
        Double temperature,
        Duration timeout) {

    public LlmProperties {
        provider = provider == null || provider.isBlank() ? "openai" : provider;
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
        modelName = modelName == null || modelName.isBlank() ? "gpt-4o-mini" : modelName;
        temperature = temperature == null ? 0.2 : temperature;
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }
}
