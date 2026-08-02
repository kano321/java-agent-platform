package com.agentplatform.codereview.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request used to index a document into the RAG vector store.
 */
public record RagDocumentRequest(
        @NotBlank(message = "sourceType is required") String sourceType,
        @NotBlank(message = "sourceId is required") String sourceId,
        @NotBlank(message = "content is required") String content) {
}
