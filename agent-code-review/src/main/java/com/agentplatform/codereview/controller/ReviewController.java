package com.agentplatform.codereview.controller;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.common.model.AgentTask;
import com.agentplatform.common.model.TaskCreateRequest;
import com.agentplatform.codereview.model.CodeReviewReport;
import com.agentplatform.codereview.model.ReviewCreateRequest;
import com.agentplatform.codereview.cache.RedisReviewCache;
import com.agentplatform.codereview.service.CodeReviewService;
import com.agentplatform.core.task.TaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST entry point for triggering and querying Java code reviews.
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final TaskService taskService;
    private final CodeReviewService reviewService;
    private final ObjectMapper objectMapper;
    private final RedisReviewCache redisReviewCache;

    public ReviewController(
            TaskService taskService,
            CodeReviewService reviewService,
            ObjectMapper objectMapper,
            RedisReviewCache redisReviewCache) {
        this.taskService = taskService;
        this.reviewService = reviewService;
        this.objectMapper = objectMapper;
        this.redisReviewCache = redisReviewCache;
    }

    @PostMapping
    public ApiResponse<AgentTask> createReview(@Valid @RequestBody ReviewCreateRequest request)
            throws JsonProcessingException {
        String input = objectMapper.writeValueAsString(request);
        TaskCreateRequest taskRequest = new TaskCreateRequest(
                "code_review_agent",
                input,
                Map.of("type", "code_review"),
                true);
        AgentTask task = taskService.createTask(taskRequest);
        taskService.runTask(task.getTaskId(), true);
        return ApiResponse.success("code review task created", task);
    }

    @GetMapping
    public ApiResponse<List<CodeReviewReport>> listReports(
            @RequestParam(required = false) String taskId) {
        List<CodeReviewReport> reports = taskId == null || taskId.isBlank()
                ? reviewService.listReports()
                : List.of(reviewService.findByTaskId(taskId));
        return ApiResponse.success(reports);
    }

    @GetMapping("/{reportId}")
    public ApiResponse<CodeReviewReport> getReport(@PathVariable String reportId) {
        return ApiResponse.success(reviewService.findReport(reportId));
    }

    @GetMapping(value = "/{reportId}/markdown", produces = MediaType.TEXT_MARKDOWN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> getMarkdown(@PathVariable String reportId) {
        var cached = redisReviewCache.getMarkdown(reportId);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }
        CodeReviewReport report = reviewService.findReport(reportId);
        redisReviewCache.putMarkdown(reportId, report.getMarkdown());
        return ResponseEntity.ok(report.getMarkdown());
    }
}
