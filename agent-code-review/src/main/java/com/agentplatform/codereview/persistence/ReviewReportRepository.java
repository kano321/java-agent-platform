package com.agentplatform.codereview.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for review reports.
 */
public interface ReviewReportRepository extends JpaRepository<ReviewReportEntity, String> {

    Optional<ReviewReportEntity> findByTaskId(String taskId);
}
