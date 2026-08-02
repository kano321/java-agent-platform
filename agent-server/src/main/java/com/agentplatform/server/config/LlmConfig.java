package com.agentplatform.server.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j integration. The model is created from environment-provided
 * settings and skipped when no API key is configured.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    @Conditional(LlmKeyCondition.class)
    public ChatModel chatLanguageModel(LlmProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.modelName())
                .temperature(properties.temperature())
                .timeout(properties.timeout())
                .build();
    }

    @Bean
    @Conditional(LlmKeyCondition.class)
    public StreamingChatModel streamingChatLanguageModel(LlmProperties properties) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.modelName())
                .temperature(properties.temperature())
                .timeout(properties.timeout())
                .build();
    }
}
