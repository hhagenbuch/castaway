package io.github.hhagenbuch.castaway.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.link.LinkState;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import io.github.hhagenbuch.castaway.tools.LinkRequirement;
import io.github.hhagenbuch.castaway.tools.impl.CalculatorTool;
import io.github.hhagenbuch.castaway.tools.impl.LivePriceTool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityGateTest {

    private final CapabilityGate gate = new CapabilityGate();
    private final AgentTool offlineCapable = new CalculatorTool();   // OFFLINE_CAPABLE
    private final AgentTool onlineOnly = new LivePriceTool();        // ONLINE_ONLY
    private final AgentTool deferrable = new StubDeferrable();       // DEFERRABLE
    private final List<AgentTool> all = List.of(offlineCapable, deferrable, onlineOnly);

    @Test
    void onlineExposesEveryToolWithNoNotice() {
        CapabilityGate.Gated gated = gate.gate(LinkState.ONLINE, all);
        assertThat(gated.tools()).containsExactlyInAnyOrder(offlineCapable, deferrable, onlineOnly);
        assertThat(gated.systemNotice()).isNull();
    }

    @Test
    void offlineHidesOnlineOnlyKeepsDeferrableAndInjectsNotice() {
        CapabilityGate.Gated gated = gate.gate(LinkState.OFFLINE, all);

        assertThat(gated.tools()).containsExactlyInAnyOrder(offlineCapable, deferrable);
        assertThat(gated.tools()).doesNotContain(onlineOnly);

        String notice = gated.systemNotice();
        assertThat(notice).contains("OFFLINE");
        assertThat(notice).contains("send_email");   // deferrable named as draft-only
        assertThat(notice).contains("live_price");    // online-only named as unavailable
        assertThat(notice.toLowerCase()).contains("queue");
    }

    private static class StubDeferrable implements AgentTool {
        @Override
        public String name() {
            return "send_email";
        }

        @Override
        public String description() {
            return "stub";
        }

        @Override
        public LinkRequirement linkRequirement() {
            return LinkRequirement.DEFERRABLE;
        }

        @Override
        public ObjectNode inputSchema(ObjectMapper mapper) {
            return mapper.createObjectNode();
        }

        @Override
        public Mono<String> execute(JsonNode input) {
            return Mono.just("stub");
        }
    }
}
