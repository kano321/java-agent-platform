package com.agentplatform.server.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Creates the LangChain4j model bean only when app.llm.api-key has text.
 */
public class LlmKeyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String value = context.getEnvironment().getProperty("app.llm.api-key");
        return value != null && !value.isBlank();
    }
}
