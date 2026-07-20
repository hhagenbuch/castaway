package io.github.hhagenbuch.castaway.llm;

import tools.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.config.AgentProperties;
import io.github.hhagenbuch.castaway.config.CastawayProperties;
import io.github.hhagenbuch.castaway.link.LinkMonitor;
import io.github.hhagenbuch.castaway.link.LinkState;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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

        StepVerifier.create(router.chat(null, List.<ObjectNode>of(), List.<AgentTool>of()))
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

        StepVerifier.create(router.chat(null, List.<ObjectNode>of(), List.<AgentTool>of()))
                .assertNext(response -> {
                    assertThat(response.text()).isEqualTo("cloud says hi");
                    assertThat(response.provenance().answeredBy()).isEqualTo("cloud:test");
                    assertThat(response.provenance().link()).isEqualTo(LinkState.ONLINE);
                })
                .verifyComplete();
    }

    @Test
    void cloudConnectivityFailureFallsBackToLocalAndHintsTheMonitor() {
        cloud.failWith(new ConnectException("connection refused")); // plug pulled mid-request
        AtomicBoolean hinted = new AtomicBoolean(false);
        LinkMonitor monitor = new LinkMonitor(Mono::empty, new CastawayProperties(null, null, null, null)) {
            @Override
            public LinkState state() {
                return LinkState.ONLINE; // monitor hasn't flipped yet
            }

            @Override
            public void hintUnreachable() {
                hinted.set(true);
            }
        };
        ModelRouter router = new ModelRouter(monitor, cloud, local, preferLocal(true));

        StepVerifier.create(router.chat(null, List.<ObjectNode>of(), List.<AgentTool>of()))
                .assertNext(response -> {
                    assertThat(response.text()).isEqualTo("local says hi");
                    assertThat(response.provenance().fellBack()).isTrue();
                    assertThat(response.provenance().render()).isEqualTo("local:test (FALLBACK)");
                })
                .verifyComplete();
        assertThat(hinted).isTrue(); // monitor nudged toward OFFLINE
    }

    @Test
    void cloudNonConnectivityErrorPropagatesInsteadOfMaskingWithLocal() {
        cloud.failWith(new IllegalStateException("HTTP 400 bad request")); // cloud reachable, unhappy
        ModelRouter router = new ModelRouter(monitorAt(LinkState.ONLINE), cloud, local, preferLocal(true));

        StepVerifier.create(router.chat(null, List.<ObjectNode>of(), List.<AgentTool>of()))
                .expectErrorMessage("HTTP 400 bad request")
                .verify();
    }

    @Test
    void degradedLocalFailureFallsBackToCloud() {
        // DEGRADED prefers local, but Ollama is down and the cloud is reachable — mirror the fallback.
        local.failWith(new ConnectException("ollama down"));
        ModelRouter router = new ModelRouter(monitorAt(LinkState.DEGRADED), cloud, local, preferLocal(true));

        StepVerifier.create(router.chat(null, List.<ObjectNode>of(), List.<AgentTool>of()))
                .assertNext(response -> {
                    assertThat(response.text()).isEqualTo("cloud says hi");
                    assertThat(response.provenance().fellBack()).isTrue();
                    assertThat(response.provenance().render()).isEqualTo("cloud:test (FALLBACK)");
                })
                .verifyComplete();
    }

    @Test
    void offlineLocalFailureHasNoCloudFallbackAndPropagates() {
        // OFFLINE: the cloud is unreachable, so there's nothing to fall back to — the
        // clear local-unavailable error must surface rather than a doomed cloud retry.
        local.failWith(new ConnectException("ollama down"));
        ModelRouter router = new ModelRouter(monitorAt(LinkState.OFFLINE), cloud, local, preferLocal(true));

        StepVerifier.create(router.chat(null, List.<ObjectNode>of(), List.<AgentTool>of()))
                .expectError(ConnectException.class)
                .verify();
    }

    private static CastawayProperties preferLocal(boolean value) {
        return new CastawayProperties(null, null, new CastawayProperties.Routing(value), null);
    }

    private static LinkMonitor monitorAt(LinkState state) {
        return new LinkMonitor(Mono::empty, new CastawayProperties(null, null, null, null)) {
            @Override
            public LinkState state() {
                return state;
            }
        };
    }

    private static class FakeCloud extends CloudLlmClient {
        private Throwable failWith;

        FakeCloud() {
            super(null, new AgentProperties("", "cloud-model", 1, 1, 1), null);
        }

        void failWith(Throwable t) {
            this.failWith = t;
        }

        @Override
        public String label() {
            return "cloud:test";
        }

        @Override
        public Mono<LlmResponse> chat(String system, List<ObjectNode> messages, Collection<AgentTool> tools) {
            return failWith != null
                    ? Mono.error(failWith)
                    : Mono.just(new LlmResponse("cloud says hi", List.of(), null, "end_turn"));
        }
    }

    private static class FakeLocal extends LocalLlmClient {
        private Throwable failWith;

        FakeLocal() {
            super(null, new CastawayProperties(null, null, null, null), null);
        }

        void failWith(Throwable t) {
            this.failWith = t;
        }

        @Override
        public String label() {
            return "local:test";
        }

        @Override
        public Mono<LlmResponse> chat(String system, List<ObjectNode> messages, Collection<AgentTool> tools) {
            return failWith != null
                    ? Mono.error(failWith)
                    : Mono.just(new LlmResponse("local says hi", List.of(), null, "end_turn"));
        }
    }
}
