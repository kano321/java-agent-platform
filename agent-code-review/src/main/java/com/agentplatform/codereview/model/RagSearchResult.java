package com.agentplatform.codereview.model;

/**
 * Similarity search hit returned by the RAG service.
 */
public record RagSearchResult(String sourceType, String sourceId, String text, double score) {
}
