package com.agentplatform.codereview.store;

import com.agentplatform.codereview.config.CodeReviewProperties;
import com.agentplatform.codereview.model.CodeReviewIssue;
import com.agentplatform.codereview.model.CodeReviewReport;
import com.agentplatform.codereview.model.Severity;
import com.agentplatform.codereview.persistence.ReviewIssueEntity;
import com.agentplatform.codereview.persistence.ReviewIssueRepository;
import com.agentplatform.codereview.persistence.ReviewReportEntity;
import com.agentplatform.codereview.persistence.ReviewReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores review records in memory, persists Markdown to disk and synchronizes
 * business data to MySQL through JPA repositories when available.
 */
@Component
public class ReviewRecordStore {

    private static final Logger log = LoggerFactory.getLogger(ReviewRecordStore.class);

    private final ConcurrentMap<String, CodeReviewReport> reports = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> reportIdsByTask = new ConcurrentHashMap<>();
    private final Path storageDir;
    private final Optional<ReviewReportRepository> reportRepository;
    private final Optional<ReviewIssueRepository> issueRepository;

    public ReviewRecordStore(CodeReviewProperties properties) {
        this(properties, Optional.empty(), Optional.empty());
    }

    @Autowired
    public ReviewRecordStore(
            CodeReviewProperties properties,
            ObjectProvider<ReviewReportRepository> reportRepositoryProvider,
            ObjectProvider<ReviewIssueRepository> issueRepositoryProvider) {
        this(properties,
                Optional.ofNullable(reportRepositoryProvider.getIfAvailable()),
                Optional.ofNullable(issueRepositoryProvider.getIfAvailable()));
    }

    private ReviewRecordStore(
            CodeReviewProperties properties,
            Optional<ReviewReportRepository> reportRepository,
            Optional<ReviewIssueRepository> issueRepository) {
        this.storageDir = Path.of(properties.storageDir()).toAbsolutePath().normalize();
        this.reportRepository = reportRepository;
        this.issueRepository = issueRepository;
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create review storage dir: " + storageDir, e);
        }
    }

    public CodeReviewReport save(CodeReviewReport report) {
        reports.put(report.getReportId(), report);
        if (report.getTaskId() != null && !report.getTaskId().isBlank()) {
            reportIdsByTask.put(report.getTaskId(), report.getReportId());
        }
        try {
            persistToDatabase(report);
        } catch (Exception e) {
            log.warn("Failed to persist review report {} to database: {}",
                    report.getReportId(), e.getMessage());
        }
        writeMarkdown(report);
        return report;
    }

    public Optional<CodeReviewReport> findById(String reportId) {
        CodeReviewReport cached = reports.get(reportId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<CodeReviewReport> fromDb = reportRepository
                .flatMap(repo -> repo.findById(reportId))
                .map(this::fromEntity);
        fromDb.ifPresent(report -> {
            reports.put(reportId, report);
            if (report.getTaskId() != null) {
                reportIdsByTask.put(report.getTaskId(), reportId);
            }
        });
        return fromDb;
    }

    public Optional<CodeReviewReport> findByTaskId(String taskId) {
        String reportId = reportIdsByTask.get(taskId);
        if (reportId != null) {
            return findById(reportId);
        }
        Optional<CodeReviewReport> fromDb = reportRepository
                .flatMap(repo -> repo.findByTaskId(taskId))
                .map(this::fromEntity);
        fromDb.ifPresent(report -> reportIdsByTask.put(taskId, report.getReportId()));
        return fromDb;
    }

    public List<CodeReviewReport> list() {
        if (reports.isEmpty() && reportRepository.isPresent()) {
            reportRepository.get().findAll().forEach(entity -> {
                CodeReviewReport report = fromEntity(entity);
                reports.put(report.getReportId(), report);
                if (report.getTaskId() != null) {
                    reportIdsByTask.put(report.getTaskId(), report.getReportId());
                }
            });
        }
        return reports.values().stream()
                .sorted(Comparator.comparing(CodeReviewReport::getCreatedAt).reversed())
                .toList();
    }

    public boolean delete(String reportId) {
        CodeReviewReport removed = reports.remove(reportId);
        if (removed != null && removed.getTaskId() != null) {
            reportIdsByTask.remove(removed.getTaskId());
        }
        reportRepository.ifPresent(repo -> repo.deleteById(reportId));
        issueRepository.ifPresent(repo -> repo.deleteByReportId(reportId));
        return removed != null;
    }

    private void persistToDatabase(CodeReviewReport report) {
        if (reportRepository.isEmpty() || issueRepository.isEmpty()) {
            return;
        }
        ReviewReportEntity entity = toEntity(report);
        reportRepository.get().save(entity);
        issueRepository.get().deleteByReportId(report.getReportId());
        for (CodeReviewIssue issue : report.getIssues()) {
            issueRepository.get().save(toEntity(report.getReportId(), issue));
        }
    }

    private ReviewReportEntity toEntity(CodeReviewReport report) {
        ReviewReportEntity entity = new ReviewReportEntity();
        entity.setReportId(report.getReportId());
        entity.setTaskId(report.getTaskId());
        entity.setRepoPath(report.getRepoPath());
        entity.setBranch(report.getBranch());
        entity.setHeadCommit(report.getHeadCommit());
        entity.setDiffBase(report.getDiffBase());
        entity.setAnalyzedFileCount(report.getAnalyzedFileCount());
        entity.setTotalJavaFiles(report.getTotalJavaFiles());
        entity.setTotalLines(report.getTotalLines());
        entity.setIssueCount(report.getIssueCount());
        entity.setLlmEnabled(report.isLlmEnabled());
        entity.setLlmInsight(report.getLlmInsight());
        entity.setSummary(report.getSummary());
        entity.setMarkdown(report.getMarkdown());
        entity.setCreatedAt(report.getCreatedAt());
        return entity;
    }

    private ReviewIssueEntity toEntity(String reportId, CodeReviewIssue issue) {
        ReviewIssueEntity entity = new ReviewIssueEntity();
        entity.setReportId(reportId);
        entity.setSeverity(issue.severity().name());
        entity.setFilePath(issue.filePath());
        entity.setLine(issue.line());
        entity.setRule(issue.rule());
        entity.setMessage(truncate(issue.message(), 2000));
        return entity;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private CodeReviewReport fromEntity(ReviewReportEntity entity) {
        CodeReviewReport report = new CodeReviewReport();
        report.setReportId(entity.getReportId());
        report.setTaskId(entity.getTaskId());
        report.setRepoPath(entity.getRepoPath());
        report.setBranch(entity.getBranch());
        report.setHeadCommit(entity.getHeadCommit());
        report.setDiffBase(entity.getDiffBase());
        report.setAnalyzedFileCount(entity.getAnalyzedFileCount() == null ? 0 : entity.getAnalyzedFileCount());
        report.setTotalJavaFiles(entity.getTotalJavaFiles() == null ? 0 : entity.getTotalJavaFiles());
        report.setTotalLines(entity.getTotalLines() == null ? 0 : entity.getTotalLines());
        report.setIssueCount(entity.getIssueCount() == null ? 0 : entity.getIssueCount());
        report.setLlmEnabled(Boolean.TRUE.equals(entity.getLlmEnabled()));
        report.setLlmInsight(entity.getLlmInsight());
        report.setSummary(entity.getSummary());
        report.setMarkdown(entity.getMarkdown());
        report.setCreatedAt(entity.getCreatedAt());
        report.setIssues(loadIssues(entity.getReportId()));
        return report;
    }

    private List<CodeReviewIssue> loadIssues(String reportId) {
        if (issueRepository.isEmpty()) {
            return List.of();
        }
        return issueRepository.get().findByReportId(reportId).stream()
                .map(entity -> new CodeReviewIssue(
                        Severity.valueOf(entity.getSeverity()),
                        entity.getFilePath(),
                        entity.getLine() == null ? 0 : entity.getLine(),
                        entity.getRule(),
                        entity.getMessage()))
                .toList();
    }

    private void writeMarkdown(CodeReviewReport report) {
        Path target = storageDir.resolve(report.getReportId() + ".md");
        try {
            Files.writeString(target, report.getMarkdown(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to persist review markdown {}: {}", target, e.getMessage());
        }
    }
}
