package com.agentplatform.codereview.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for a persisted code review report.
 */
@Entity
@Table(name = "code_review_report")
public class ReviewReportEntity {

    @Id
    private String reportId;

    private String taskId;

    @Column(columnDefinition = "LONGTEXT")
    private String repoPath;

    private String branch;
    private String headCommit;
    private String diffBase;
    private Integer analyzedFileCount;
    private Integer totalJavaFiles;
    private Integer totalLines;
    private Integer issueCount;
    private Boolean llmEnabled;

    @Column(columnDefinition = "LONGTEXT")
    private String llmInsight;

    @Column(columnDefinition = "LONGTEXT")
    private String summary;

    @Column(columnDefinition = "LONGTEXT")
    private String markdown;

    private Instant createdAt;

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getHeadCommit() {
        return headCommit;
    }

    public void setHeadCommit(String headCommit) {
        this.headCommit = headCommit;
    }

    public String getDiffBase() {
        return diffBase;
    }

    public void setDiffBase(String diffBase) {
        this.diffBase = diffBase;
    }

    public Integer getAnalyzedFileCount() {
        return analyzedFileCount;
    }

    public void setAnalyzedFileCount(Integer analyzedFileCount) {
        this.analyzedFileCount = analyzedFileCount;
    }

    public Integer getTotalJavaFiles() {
        return totalJavaFiles;
    }

    public void setTotalJavaFiles(Integer totalJavaFiles) {
        this.totalJavaFiles = totalJavaFiles;
    }

    public Integer getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(Integer totalLines) {
        this.totalLines = totalLines;
    }

    public Integer getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(Integer issueCount) {
        this.issueCount = issueCount;
    }

    public Boolean getLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(Boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public String getLlmInsight() {
        return llmInsight;
    }

    public void setLlmInsight(String llmInsight) {
        this.llmInsight = llmInsight;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
