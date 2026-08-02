package com.agentplatform.codereview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the code review module.
 */
@ConfigurationProperties(prefix = "app.review")
public record CodeReviewProperties(String storageDir, int maxFiles) {

    public CodeReviewProperties {
        storageDir = storageDir == null || storageDir.isBlank() ? "data/reports" : storageDir;
        maxFiles = maxFiles <= 0 ? 200 : maxFiles;
    }
}
