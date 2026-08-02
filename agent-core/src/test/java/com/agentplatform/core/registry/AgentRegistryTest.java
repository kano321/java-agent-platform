package com.agentplatform.core.registry;

import com.agentplatform.common.model.AgentInfo;
import com.agentplatform.common.model.AgentKind;
import com.agentplatform.common.model.AgentRegistrationRequest;
import com.agentplatform.common.model.AgentStatus;
import com.agentplatform.core.agent.DemoAgent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRegistryTest {

    private final AgentRegistry registry = new AgentRegistry();
    private final DemoAgent demoAgent = new DemoAgent();

    @Test
    void registersLocalAgentAndHeartbeats() {
        AgentInfo info = registry.register(demoAgent);

        assertThat(info.agentId()).isEqualTo("demo_agent");
        assertThat(info.status()).isEqualTo(AgentStatus.ACTIVE);
        assertThat(registry.listAgentInfos()).hasSize(1);

        AgentInfo heartbeat = registry.heartbeat(demoAgent.agentId());
        assertThat(heartbeat.lastHeartbeatAt()).isAfterOrEqualTo(info.lastHeartbeatAt());
    }

    @Test
    void unregistersAgent() {
        registry.register(demoAgent);

        AgentInfo removed = registry.unregister("demo_agent");

        assertThat(removed.status()).isEqualTo(AgentStatus.OFFLINE);
        assertThat(registry.contains("demo_agent")).isFalse();
    }

    @Test
    void removesExpiredRemoteAgent() {
        AgentRegistrationRequest request = new AgentRegistrationRequest(
                "remote_1", "Remote", "remote test", AgentKind.REMOTE,
                "1.0.0", List.of("test"), "http://localhost:9999/execute");
        registry.registerRemote(request);
        registry.heartbeat("remote_1", Instant.now().minusSeconds(120));

        List<String> removed = registry.removeExpired(Duration.ofSeconds(60), Instant.now());

        assertThat(removed).containsExactly("remote_1");
        assertThat(registry.contains("remote_1")).isFalse();
    }
}
