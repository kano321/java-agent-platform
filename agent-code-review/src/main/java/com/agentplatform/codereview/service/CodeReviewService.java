package com.agentplatform.codereview.service;

import com.agentplatform.codereview.model.CodeReviewIssue;
import com.agentplatform.codereview.model.CodeReviewReport;
import com.agentplatform.codereview.model.GitRepositoryInfo;
import com.agentplatform.codereview.model.JavaFileSnapshot;
import com.agentplatform.codereview.model.ReviewCreateRequest;
import com.agentplatform.codereview.model.Severity;
import com.agentplatform.codereview.model.RagSearchResult;
import com.agentplatform.codereview.rag.ReviewRagService;
import com.agentplatform.codereview.store.ReviewRecordStore;
import com.agentplatform.codereview.util.GitRepositoryScanner;
import com.agentplatform.codereview.util.JavaSourceAnalyzer;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestrates Java code review: scan repository, analyze sources, optionally
 * enrich with LLM, generate Markdown and persist the report.
 */
@Service
public class CodeReviewService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);

    private final JavaSourceAnalyzer javaSourceAnalyzer;
    private final GitRepositoryScanner gitRepositoryScanner;
    private final MarkdownReportGenerator markdownReportGenerator;
    private final ReviewRecordStore reviewRecordStore;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ReviewRagService reviewRagService;

    public CodeReviewService(
            JavaSourceAnalyzer javaSourceAnalyzer,
            GitRepositoryScanner gitRepositoryScanner,
            MarkdownReportGenerator markdownReportGenerator,
            ReviewRecordStore reviewRecordStore,
            ObjectProvider<ChatModel> chatModelProvider,
            ReviewRagService reviewRagService) {
        this.javaSourceAnalyzer = javaSourceAnalyzer;
        this.gitRepositoryScanner = gitRepositoryScanner;
        this.markdownReportGenerator = markdownReportGenerator;
        this.reviewRecordStore = reviewRecordStore;
        this.chatModelProvider = chatModelProvider;
        this.reviewRagService = reviewRagService;
    }

    public CodeReviewReport runReview(ReviewCreateRequest request, String taskId) {
        Path repo = Path.of(request.repoPath()).toAbsolutePath().normalize();
        GitRepositoryInfo repoInfo = gitRepositoryScanner.scan(repo, request.diffBase(), request.maxFiles());

        List<JavaFileSnapshot> files = new ArrayList<>();
        List<CodeReviewIssue> allIssues = new ArrayList<>();
        int totalLines = 0;
        for (String relativePath : repoInfo.javaFilePaths()) {
            Path javaFile = repo.resolve(relativePath);
            JavaFileSnapshot snapshot = javaSourceAnalyzer.analyze(repo, javaFile);
            files.add(snapshot);
            allIssues.addAll(snapshot.issues());
            totalLines += snapshot.lineCount();
        }

        CodeReviewReport report = new CodeReviewReport();
        report.setReportId("review_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999));
        report.setTaskId(taskId);
        report.setRepoPath(repo.toString());
        report.setBranch(repoInfo.branch());
        report.setHeadCommit(repoInfo.headCommit());
        report.setDiffBase(repoInfo.diffBase());
        report.setAnalyzedFileCount(files.size());
        report.setTotalJavaFiles(repoInfo.totalTrackedJavaFiles());
        report.setTotalLines(totalLines);
        report.setFiles(files);
        report.setIssues(allIssues);
        report.setIssueCount(allIssues.size());
        report.setCreatedAt(Instant.now());

        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            report.setLlmEnabled(true);
            try {
                report.setLlmInsight(model.chat(buildLlmPrompt(report, request.focus())));
            } catch (Exception e) {
                log.warn("LLM review failed: {}", e.getMessage());
                report.setLlmInsight("LLM review failed: " + e.getMessage());
            }
        }

        report.setSummary(buildSummary(report));
        report.setMarkdown(markdownReportGenerator.generate(report));
        CodeReviewReport saved = reviewRecordStore.save(report);
        try {
            reviewRagService.indexDocument("review", saved.getReportId(), saved.getMarkdown());
        } catch (Exception e) {
            log.warn("RAG indexing failed: {}", e.getMessage());
        }
        return saved;
    }

    public CodeReviewReport findReport(String reportId) {
        return reviewRecordStore.findById(reportId)
                .orElseThrow(() -> new com.agentplatform.codereview.exception.ReviewReportNotFoundException(reportId));
    }

    public List<CodeReviewReport> listReports() {
        return reviewRecordStore.list();
    }

    public CodeReviewReport findByTaskId(String taskId) {
        return reviewRecordStore.findByTaskId(taskId)
                .orElseThrow(() -> new com.agentplatform.codereview.exception.ReviewReportNotFoundException(taskId));
    }

    private String buildSummary(CodeReviewReport report) {
        long critical = countBySeverity(report, Severity.CRITICAL);
        long major = countBySeverity(report, Severity.MAJOR);
        long minor = countBySeverity(report, Severity.MINOR);
        long info = countBySeverity(report, Severity.INFO);
        return String.format(
                "Analyzed %d Java files (%d tracked) and %d lines. Found %d issues: %d critical, %d major, %d minor, %d info.",
                report.getAnalyzedFileCount(),
                report.getTotalJavaFiles(),
                report.getTotalLines(),
                report.getIssueCount(),
                critical,
                major,
                minor,
                info);
    }

    private long countBySeverity(CodeReviewReport report, Severity severity) {
        return report.getIssues().stream()
                .filter(issue -> issue.severity() == severity)
                .count();
    }

    private String buildLlmPrompt(CodeReviewReport report, String focus) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior Java code reviewer. Review focus: ").append(focus).append(".\n\n");
        sb.append("Repository: ").append(report.getRepoPath()).append("\n");
        sb.append("Branch: ").append(report.getBranch()).append("\n");
        sb.append("Files analyzed: ").append(report.getAnalyzedFileCount()).append("\n");
        sb.append("Total lines: ").append(report.getTotalLines()).append("\n\n");
        sb.append("Top findings:\n");
        report.getIssues().stream().limit(60).forEach(issue ->
                sb.append("- [").append(issue.severity()).append("] ")
                        .append(issue.filePath()).append(":").append(issue.line())
                        .append(" ").append(issue.rule()).append(" - ").append(issue.message()).append("\n"));
        sb.append("\nProvide concise architecture, correctness, performance, security and testability suggestions. ");
        sb.append("Do not invent issues that are not listed.");
        try {
            List<RagSearchResult> similar = reviewRagService.search(report.getRepoPath(), 3);
            if (!similar.isEmpty()) {
                sb.append("\n\nRelated historical review snippets:\n");
                similar.forEach(hit -> sb.append("- ").append(hit.text()).append("\n"));
            }
        } catch (Exception e) {
            log.debug("RAG context skipped: {}", e.getMessage());
        }
        return sb.toString();
    }
}
