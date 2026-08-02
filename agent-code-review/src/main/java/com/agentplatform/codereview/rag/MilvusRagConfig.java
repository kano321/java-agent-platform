package com.agentplatform.codereview.rag;

import com.agentplatform.codereview.config.RagProperties;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.MetricType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates a Milvus-backed embedding store when Milvus is enabled.
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class MilvusRagConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.rag", name = "milvus-enabled", havingValue = "true")
    public MilvusEmbeddingStore milvusEmbeddingStore(RagProperties properties) {
        return MilvusEmbeddingStore.builder()
                .host(properties.milvusHost())
                .port(properties.milvusPort())
                .collectionName(properties.milvusCollectionName())
                .databaseName(emptyToNull(properties.milvusDatabaseName()))
                .username(emptyToNull(properties.milvusUsername()))
                .password(emptyToNull(properties.milvusPassword()))
                .dimension(HashEmbeddingModel.DIMENSION)
                .metricType(MetricType.COSINE)
                .consistencyLevel(ConsistencyLevelEnum.STRONG)
                .autoFlushOnInsert(true)
                .build();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
