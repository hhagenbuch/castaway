package io.github.hhagenbuch.castaway.llm;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.config.AgentProperties;
import io.github.hhagenbuch.castaway.config.CastawayProperties;
import io.github.hhagenbuch.castaway.link.LinkMonitor;
import io.github.hhagenbuch.castaway.link.LinkState;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRouterTest {

    private final FakeCloud cloud = new FakeCloud();
    private final FakeLocal local = new FakeLocal();

    @Test
    void routesByLinkState() {
        ModelRouter router = new ModelRouter(monitorAt(LinkState.ONLINE), cloud, local, preferLocal(true));
        assertThat(router.route(LinkState.ONLINE)).isSameAs(cloud);
        assertThat(router.route(LinkState.OFFLINE)).isSameAs(local);
        assertThat(router.route(LinkState.DEGRADED)).isSameAs(local); // default policy prefers local
    }

    @Test
    void degradedCanBeConfiguredToPreferCloud() {
        ModelRouter router = new ModelRouter(monitorAt(LinkState.DEGRADED), cloud, local, preferLocal(false));
        assertThat(router.route(LinkState.DEGRADED)).isSameAs(cloud);
    }

    @Test
    void offlineAnswerIsTaggedWithLocalProvenance() {
        ModelRouter router = new ModelRouter(monitorAt(LinkState.OFFLINE), cloud, local, preferLocal(true));

        StepVerifier.create(router.chat(List.<ObjectNode>of(), List.<AgentTool>of()))
                .assertNext(response -> {
                    assertThat(response.text()).isEqualTo("local says hi");
                    assertThat(response.provenance().answeredBy()).isEqualTo("local:test");
                    assertThat(response.provenance().link()).isEqualTo(LinkState.OFFLINE);
                    assertThat(response.provenance().render()).isEqualTo("local:test (OFFLINE)");
                })
                .verifyComplete();
    }

    @Test
    void onlineAnswerIsTaggedWithCloudProvenance() {
        ModelRouter router = new ModelRouter(monitorAt(LinkState.ONLINE), cloud, local, preferLocal(true));

        StepVerifier.create(router.chat(List.<ObjectNode>of(), List.<AgentTool>of()))
                .assertNext(response -> {
                    assertThat(response.text()).isEqualTo("cloud says hi");
                    assertThat(response.provenance().answeredBy()).isEqualTo("cloud:test");
                    assertThat(response.provenance().link()).isEqualTo(LinkState.ONLINE);
                })
                .verifyComplete();
    }

    private static CastawayProperties preferLocal(boolean value) {
        return new CastawayProperties(null, null, new CastawayProperties.Routing(value));
    }

    private static LinkMonitor monitorAt(LinkState state) {
        return new LinkMonitor(Mono::empty, new CastawayProperties(null, null, null)) {
            @Override
            public LinkState state() {
                return state;
            }
        };
    }

    private static class FakeCloud extends CloudLlmClient {
        FakeCloud() {
            super(null, new AgentProperties("", "cloud-model", 1, 1, 1), null);
        }

        @Override
        public String label() {
            return "cloud:test";
        }

        @Override
        public Mono<LlmResponse> chat(List<ObjectNode> messages, Collection<AgentTool> tools) {
            return Mono.just(new LlmResponse("cloud says hi", List.of(), null, "end_turn"));
        }
    }

    private static class FakeLocal extends LocalLlmClient {
        FakeLocal() {
            super(null, new CastawayProperties(null, null, null), null);
        }

        @Override
        public String label() {
            return "local:test";
        }

        @Override
        public Mono<LlmResponse> chat(List<ObjectNode> messages, Collection<AgentTool> tools) {
            return Mono.just(new LlmResponse("local says hi", List.of(), null, "end_turn"));
        }
    }
}
