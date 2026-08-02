package com.agentplatform.core.agent;

import com.agentplatform.common.model.AgentKind;
import com.agentplatform.common.model.AgentRegistrationRequest;
import com.agentplatform.core.task.TaskExecutionContext;
import com.agentplatform.core.task.TaskLogSink;

import java.util.List;

/**
 * Contract implemented by every executable agent.
 */
public interface Agent {

    String agentId();

    String name();

    String description();

    AgentKind kind();

    String version();

    List<String> tags();

    /**
     * Executes the agent and returns a text result.
     */
    String execute(TaskExecutionContext context, TaskLogSink logSink) throws Exception;

    default AgentRegistrationRequest registrationRequest() {
        return new AgentRegistrationRequest(
                agentId(), name(), description(), kind(), version(), tags(), null);
    }
}
