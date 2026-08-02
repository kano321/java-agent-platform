package com.agentplatform.codereview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the RAG vector store. Milvus is used when enabled;
 * otherwise an in-memory embedding store is created.
 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        boolean milvusEnabled,
        String milvusHost,
        int milvusPort,
        String milvusCollectionName,
        String milvusDatabaseName,
        String milvusUsername,
        String milvusPassword) {

    public RagProperties {
        milvusHost = milvusHost == null || milvusHost.isBlank() ? "localhost" : milvusHost;
        milvusPort = milvusPort <= 0 ? 19530 : milvusPort;
        milvusCollectionName = milvusCollectionName == null || milvusCollectionName.isBlank()
                ? "agent_platform_embeddings"
                : milvusCollectionName;
        milvusDatabaseName = milvusDatabaseName == null ? "" : milvusDatabaseName;
        milvusUsername = milvusUsername == null ? "" : milvusUsername;
        milvusPassword = milvusPassword == null ? "" : milvusPassword;
    }
}
