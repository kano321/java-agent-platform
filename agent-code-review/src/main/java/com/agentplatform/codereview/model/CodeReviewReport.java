package com.agentplatform.codereview.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Complete code review report including generated Markdown.
 */
public class CodeReviewReport {

    private String reportId;
    private String taskId;
    private String repoPath;
    private String branch;
    private String headCommit;
    private String diffBase;
    private int analyzedFileCount;
    private int totalJavaFiles;
    private int totalLines;
    private int issueCount;
    private boolean llmEnabled;
    private String llmInsight;
    private String summary;
    private String markdown;
    private Instant createdAt;
    private List<CodeReviewIssue> issues = new ArrayList<>();
    private List<JavaFileSnapshot> files = new ArrayList<>();

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

    public int getAnalyzedFileCount() {
        return analyzedFileCount;
    }

    public void setAnalyzedFileCount(int analyzedFileCount) {
        this.analyzedFileCount = analyzedFileCount;
    }

    public int getTotalJavaFiles() {
        return totalJavaFiles;
    }

    public void setTotalJavaFiles(int totalJavaFiles) {
        this.totalJavaFiles = totalJavaFiles;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(int totalLines) {
        this.totalLines = totalLines;
    }

    public int getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(int issueCount) {
        this.issueCount = issueCount;
    }

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
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

    public List<CodeReviewIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<CodeReviewIssue> issues) {
        this.issues = issues == null ? new ArrayList<>() : new ArrayList<>(issues);
    }

    public List<JavaFileSnapshot> getFiles() {
        return files;
    }

    public void setFiles(List<JavaFileSnapshot> files) {
        this.files = files == null ? new ArrayList<>() : new ArrayList<>(files);
    }
}
