package io.github.hhagenbuch.castaway.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.capability.CapabilityGate;
import io.github.hhagenbuch.castaway.config.AgentProperties;
import io.github.hhagenbuch.castaway.config.CastawayProperties;
import io.github.hhagenbuch.castaway.link.LinkMonitor;
import io.github.hhagenbuch.castaway.link.LinkState;
import io.github.hhagenbuch.castaway.llm.LlmClient;
import io.github.hhagenbuch.castaway.llm.LlmResponse;
import io.github.hhagenbuch.castaway.llm.Provenance;
import io.github.hhagenbuch.castaway.tools.ToolRegistry;
import io.github.hhagenbuch.castaway.tools.impl.CalculatorTool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentProperties props = new AgentProperties("", "test-model", 512, 3, 1);
    private final ToolRegistry registry = new ToolRegistry(List.of(new CalculatorTool()));
    private final ConversationMemory memory = new ConversationMemory();
    // ONLINE monitor + gate: gate returns all tools and no notice, so the loop behaves plainly.
    private final LinkMonitor link = new LinkMonitor(Mono::empty, new CastawayProperties(null, null, null, null));
    private final CapabilityGate gate = new CapabilityGate();

    private AgentLoop loopWith(LlmClient llm) {
        return new AgentLoop(llm, registry, memory, props, mapper, link, gate);
    }

    @Test
    void plainAnswerCarriesProvenanceThrough() {
        Provenance offline = new Provenance("local:qwen3:8b", LinkState.OFFLINE);
        LlmClient fake = (system, messages, tools) -> Mono.just(textResponse("Hello there").withProvenance(offline));
        AgentLoop loop = loopWith(fake);

        StepVerifier.create(loop.run("s1", "hi"))
                .expectNextMatches(r -> r.answer().equals("Hello there")
                        && r.toolsUsed().isEmpty()
                        && r.provenance().equals(offline))
                .verifyComplete();
        assertThat(memory.history("s1")).hasSize(2); // user + assistant
    }

    @Test
    void errorDoesNotBrickTheSession() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        LlmClient flaky = (system, messages, tools) -> calls.getAndIncrement() == 0
                ? Mono.error(new RuntimeException("boom"))
                : Mono.just(textResponse("recovered"));
        AgentLoop loop = loopWith(flaky);

        StepVerifier.create(loop.run("s2", "first"))
                .expectNextMatches(r -> r.answer().contains("internal error") && r.provenance() == null)
                .verifyComplete();
        assertThat(memory.history("s2")).hasSize(2);

        StepVerifier.create(loop.run("s2", "second"))
                .expectNextMatches(r -> r.answer().equals("recovered"))
                .verifyComplete();
        assertThat(memory.history("s2")).hasSize(4);
    }

    private LlmResponse textResponse(String text) {
        ArrayNode content = mapper.createArrayNode();
        ObjectNode block = content.addObject();
        block.put("type", "text");
        block.put("text", text);
        return new LlmResponse(text, List.of(), content, "end_turn");
    }
}
