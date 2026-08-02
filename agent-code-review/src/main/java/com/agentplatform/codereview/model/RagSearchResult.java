package com.agentplatform.codereview.model;

/**
 * Similarity search hit returned by the RAG service.
 */
public record RagSearchResult(String text, double score) {
}
