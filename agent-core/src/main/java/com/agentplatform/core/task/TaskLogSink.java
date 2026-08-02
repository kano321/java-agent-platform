package com.agentplatform.core.task;

/**
 * Log sink passed to an agent during execution.
 */
public interface TaskLogSink {

    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message);
}
