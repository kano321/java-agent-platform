package com.agentplatform.codereview.controller;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.codereview.model.RagDocumentRequest;
import com.agentplatform.codereview.model.RagSearchResult;
import com.agentplatform.codereview.persistence.RagDocumentEntity;
import com.agentplatform.codereview.rag.ReviewRagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for RAG indexing and similarity search.
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final ReviewRagService ragService;

    public RagController(ReviewRagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/documents")
    public ApiResponse<String> indexDocument(@Valid @RequestBody RagDocumentRequest request) {
        return ApiResponse.success(ragService.indexDocument(
                request.sourceType(),
                request.sourceId(),
                request.content()));
    }

    @GetMapping("/search")
    public ApiResponse<List<RagSearchResult>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.success(ragService.search(query, limit));
    }

    @GetMapping("/documents")
    public ApiResponse<List<RagDocumentEntity>> listDocuments() {
        return ApiResponse.success(ragService.listDocuments());
    }
}
