package com.agentplatform.codereview.exception;

import com.agentplatform.common.exception.AgentException;

/**
 * Thrown when a review report does not exist.
 */
public class ReviewReportNotFoundException extends AgentException {

    public ReviewReportNotFoundException(String reportId) {
        super("Review report not found: " + reportId);
    }
}
