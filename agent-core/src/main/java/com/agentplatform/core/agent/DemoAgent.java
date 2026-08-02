package com.agentplatform.core.agent;

import com.agentplatform.common.model.AgentKind;
import com.agentplatform.core.task.TaskExecutionContext;
import com.agentplatform.core.task.TaskLogSink;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Local demo agent used to verify the task pipeline and SSE streaming without
 * calling any external LLM service.
 */
@Component
public class DemoAgent extends AbstractAgent {

    public DemoAgent() {
        super(
                "demo_agent",
                "Demo Agent",
                "Local demo agent that simulates a multi-step task and streams logs",
                AgentKind.DEMO,
                "1.0.0",
                List.of("demo", "local", "sse"));
    }

    @Override
    public String execute(TaskExecutionContext context, TaskLogSink logSink) throws InterruptedException {
        String target = context.getInput();

        logSink.info("Demo agent started, taskId=" + context.getTaskId());
        logSink.info("Step 1/3: collecting input context");
        Thread.sleep(300);
        if (context.isCancelled()) {
            logSink.warn("Task cancelled after step 1");
            return "cancelled";
        }

        logSink.info("Step 2/3: analyzing input: " + target);
        Thread.sleep(300);
        if (context.isCancelled()) {
            logSink.warn("Task cancelled after step 2");
            return "cancelled";
        }

        logSink.info("Step 3/3: generating demo result");
        Thread.sleep(200);
        if (context.isCancelled()) {
            logSink.warn("Task cancelled after step 3");
            return "cancelled";
        }

        logSink.info("Demo agent finished");
        return "Demo analysis completed for: " + target;
    }
}
