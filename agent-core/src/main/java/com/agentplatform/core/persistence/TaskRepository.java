package com.agentplatform.core.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for agent tasks.
 */
public interface TaskRepository extends JpaRepository<TaskEntity, String> {
}
