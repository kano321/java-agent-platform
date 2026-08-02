package com.agentplatform.codereview.agent;

import com.agentplatform.common.exception.AgentExecutionException;
import com.agentplatform.common.model.AgentKind;
import com.agentplatform.codereview.model.CodeReviewReport;
import com.agentplatform.codereview.model.ReviewCreateRequest;
import com.agentplatform.codereview.service.CodeReviewService;
import com.agentplatform.core.agent.AbstractAgent;
import com.agentplatform.core.task.TaskExecutionContext;
import com.agentplatform.core.task.TaskLogSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Local code review agent registered into the platform registry.
 */
@Component
public class CodeReviewAgent extends AbstractAgent {

    private final CodeReviewService reviewService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodeReviewAgent(CodeReviewService reviewService) {
        super(
                "code_review_agent",
                "Java Code Review Agent",
                "Analyzes local Java repositories with JavaParser and JGit, generates Markdown review reports",
                AgentKind.CODE_REVIEW,
                "1.0.0",
                List.of("java", "code-review", "javalang", "git"));
        this.reviewService = reviewService;
    }

    @Override
    public String execute(TaskExecutionContext context, TaskLogSink logSink) throws Exception {
        String input = context.getInput().trim();
        ReviewCreateRequest request = parseInput(input);
        logSink.info("Code review started for " + request.repoPath());
        logSink.info("Diff base: " + (request.diffBase() == null ? "HEAD" : request.diffBase()));

        CodeReviewReport report = reviewService.runReview(request, context.getTaskId());
        logSink.info("Code review completed, files=" + report.getAnalyzedFileCount()
                + ", issues=" + report.getIssueCount());
        return report.getMarkdown();
    }

    private ReviewCreateRequest parseInput(String input) {
        try {
            return objectMapper.readValue(input, ReviewCreateRequest.class);
        } catch (Exception e) {
            Path candidate = Path.of(input);
            if (Files.isDirectory(candidate)) {
                return new ReviewCreateRequest(input, null, null, null, null);
            }
            throw new AgentExecutionException(
                    "Invalid code review input, expected JSON or repository path: " + input);
        }
    }
}
