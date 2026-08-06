package com.agentplatform.codereview.rag;

import com.agentplatform.codereview.model.RagSearchResult;
import com.agentplatform.codereview.persistence.RagDocumentEntity;
import com.agentplatform.codereview.persistence.RagDocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Indexes review reports and searches historical knowledge with embeddings.
 */
@Service
public class ReviewRagService {

    private static final Logger log = LoggerFactory.getLogger(ReviewRagService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RagDocumentRepository documentRepository;

    public ReviewRagService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            RagDocumentRepository documentRepository) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentRepository = documentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reindex() {
        List<RagDocumentEntity> documents = documentRepository.findAll();
        for (RagDocumentEntity document : documents) {
            try {
                Embedding embedding = embeddingModel.embed(document.getContent()).content();
                embeddingStore.addAll(
                        List.of(document.getId()),
                        List.of(embedding),
                        List.of(TextSegment.from(document.getContent())));
            } catch (Exception e) {
                log.warn("Failed to reindex RAG document {}: {}", document.getId(), e.getMessage());
            }
        }
        if (!documents.isEmpty()) {
            log.info("Reindexed {} RAG documents", documents.size());
        }
    }

    public String indexDocument(String sourceType, String sourceId, String content) {
        RagDocumentEntity entity = new RagDocumentEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setContent(content);
        entity.setCreatedAt(Instant.now());
        documentRepository.save(entity);

        Embedding embedding = embeddingModel.embed(content).content();
        embeddingStore.addAll(
                List.of(entity.getId()),
                List.of(embedding),
                List.of(TextSegment.from(content)));
        return entity.getId();
    }

    public List<RagSearchResult> search(String query, int limit) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(Math.min(Math.max(limit, 1), 20))
                .build();
        Map<String, RagDocumentEntity> documentsById = documentRepository.findAll().stream()
                .collect(Collectors.toMap(RagDocumentEntity::getId, document -> document));
        return embeddingStore.search(request).matches().stream()
                .map(match -> toSearchResult(match, query, documentsById))
                .toList();
    }

    public List<RagDocumentEntity> listDocuments() {
        return documentRepository.findAll();
    }

    private RagSearchResult toSearchResult(
            EmbeddingMatch<TextSegment> match,
            String query,
            Map<String, RagDocumentEntity> documentsById) {
        RagDocumentEntity document = documentsById.get(match.embeddingId());
        if (document == null) {
            return new RagSearchResult(
                    "unknown",
                    match.embeddingId(),
                    buildSnippet(match.embedded().text(), query),
                    match.score());
        }
        return new RagSearchResult(
                document.getSourceType(),
                document.getSourceId(),
                buildSnippet(document.getContent(), query),
                match.score());
    }

    private String buildSnippet(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        String lowerQuery = query == null ? "" : query.trim().toLowerCase();
        int maxLength = 240;
        if (lowerQuery.isBlank()) {
            return normalized.length() <= maxLength
                    ? normalized
                    : normalized.substring(0, maxLength) + "...";
        }
        int index = normalized.toLowerCase().indexOf(lowerQuery);
        if (index < 0) {
            return normalized.length() <= maxLength
                    ? normalized
                    : normalized.substring(0, maxLength) + "...";
        }
        int start = Math.max(0, index - 80);
        int end = Math.min(normalized.length(), index + lowerQuery.length() + 160);
        String snippet = normalized.substring(start, end);
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < normalized.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }
}
