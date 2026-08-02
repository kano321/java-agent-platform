package com.agentplatform.codereview.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed cache for generated review Markdown. Failures degrade silently
 * so local development and tests can run without a Redis instance.
 */
@Component
public class RedisReviewCache {

    private static final Logger log = LoggerFactory.getLogger(RedisReviewCache.class);
    private static final String KEY_PREFIX = "review:markdown:";

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;

    public RedisReviewCache(
            StringRedisTemplate redisTemplate,
            @Value("${app.cache.redis-enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
    }

    public void putMarkdown(String reportId, String markdown) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + reportId, markdown, Duration.ofHours(1));
        } catch (Exception e) {
            log.debug("Redis cache write skipped: {}", e.getMessage());
        }
    }

    public Optional<String> getMarkdown(String reportId) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + reportId));
        } catch (Exception e) {
            log.debug("Redis cache read skipped: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
