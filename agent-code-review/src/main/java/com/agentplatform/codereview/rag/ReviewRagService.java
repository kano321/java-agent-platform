package com.agentplatform.codereview.rag;

import com.agentplatform.codereview.model.RagSearchResult;
import com.agentplatform.codereview.persistence.RagDocumentEntity;
import com.agentplatform.codereview.persistence.RagDocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        return embeddingStore.search(request).matches().stream()
                .map(match -> new RagSearchResult(match.embedded().text(), match.score()))
                .toList();
    }

    public List<RagDocumentEntity> listDocuments() {
        return documentRepository.findAll();
    }
}
