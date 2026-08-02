package com.agentplatform.codereview.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for review issues.
 */
public interface ReviewIssueRepository extends JpaRepository<ReviewIssueEntity, Long> {

    void deleteByReportId(String reportId);

    List<ReviewIssueEntity> findByReportId(String reportId);
}
