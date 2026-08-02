package com.agentplatform.core.task;

import com.agentplatform.common.model.TaskLogLevel;

/**
 * Adapts the task log broker to the agent-facing log sink interface.
 */
public class BrokerTaskLogSink implements TaskLogSink {

    private final TaskLogBroker broker;
    private final String taskId;

    public BrokerTaskLogSink(TaskLogBroker broker, String taskId) {
        this.broker = broker;
        this.taskId = taskId;
    }

    @Override
    public void debug(String message) {
        broker.append(taskId, TaskLogLevel.DEBUG, message);
    }

    @Override
    public void info(String message) {
        broker.append(taskId, TaskLogLevel.INFO, message);
    }

    @Override
    public void warn(String message) {
        broker.append(taskId, TaskLogLevel.WARN, message);
    }

    @Override
    public void error(String message) {
        broker.append(taskId, TaskLogLevel.ERROR, message);
    }
}
