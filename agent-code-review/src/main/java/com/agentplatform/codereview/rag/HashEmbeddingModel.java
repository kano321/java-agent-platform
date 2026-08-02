package com.agentplatform.codereview.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Local deterministic embedding model used when no external embedding API is
 * configured. It hashes character n-grams into a fixed-size normalized vector.
 */
@Component
public class HashEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSION = 256;

    @Override
    public int dimension() {
        return DIMENSION;
    }

    @Override
    public String modelName() {
        return "hash-embedding-v1";
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        List<Embedding> embeddings = request.inputs().stream()
                .map(input -> Embedding.from(vectorize(input.text())))
                .toList();
        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .modelName(modelName())
                .build();
    }

    private float[] vectorize(String text) {
        float[] vector = new float[DIMENSION];
        String normalized = (text == null ? "" : text).toLowerCase();
        for (int i = 0; i + 3 < normalized.length(); i++) {
            int hash = Math.floorMod(normalized.substring(i, i + 4).hashCode(), DIMENSION);
            vector[hash] += 1f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0) {
            double sqrt = Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= (float) sqrt;
            }
        }
        return vector;
    }
}
