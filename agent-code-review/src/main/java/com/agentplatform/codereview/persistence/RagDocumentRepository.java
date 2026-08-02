package com.agentplatform.codereview.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for RAG documents.
 */
public interface RagDocumentRepository extends JpaRepository<RagDocumentEntity, String> {
}
