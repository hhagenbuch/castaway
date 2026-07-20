/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter),
 * repackaged into castaway per DESIGN.md section 6. Changes: the loop threads the
 * final turn's Provenance into AgentResult; the streaming variant is dropped (not
 * used in Phase 1). The error-path memory-rollback fix is retained.
 */
package io.github.hhagenbuch.castaway.core;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.capability.CapabilityGate;
import io.github.hhagenbuch.castaway.config.AgentProperties;
import io.github.hhagenbuch.castaway.link.LinkMonitor;
import io.github.hhagenbuch.castaway.llm.LlmClient;
import io.github.hhagenbuch.castaway.llm.LlmResponse;
import io.github.hhagenbuch.castaway.llm.ToolCall;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import io.github.hhagenbuch.castaway.tools.ToolRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The agentic core: model -> (tool calls -> results -> model)* -> answer. The
 * injected {@link LlmClient} is castaway's {@code ModelRouter}, so the loop is
 * link-agnostic — it never knows whether the cloud or the local model answered.
 */
@Component
public class AgentLoop {

    private final LlmClient llm;
    private final ToolRegistry registry;
    private final ConversationMemory memory;
    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final LinkMonitor link;
    private final CapabilityGate gate;

    public AgentLoop(LlmClient llm, ToolRegistry registry, ConversationMemory memory,
                     AgentProperties props, ObjectMapper mapper, LinkMonitor link, CapabilityGate gate) {
        this.llm = llm;
        this.registry = registry;
        this.memory = memory;
        this.props = props;
        this.mapper = mapper;
        this.link = link;
        this.gate = gate;
    }

    public Mono<AgentResult> run(String sessionId, String userMessage) {
        List<ObjectNode> messages = memory.history(sessionId);
        int mark = messages.size();
        messages.add(textMessage("user", userMessage));
        List<String> toolsUsed = new ArrayList<>();
        // Rewrite the visible toolset and system notice for the current link state, so
        // the model only sees usable tools and knows when it's degraded.
        CapabilityGate.Gated gated = gate.gate(link.state(), registry.all());
        return step(messages, 0, toolsUsed, gated.systemNotice(), gated.tools())
                .map(response -> new AgentResult(response.text(), List.copyOf(toolsUsed), response.provenance()))
                .onErrorResume(e -> Mono.just(new AgentResult(
                        recordFailure(messages, mark, userMessage, e), List.copyOf(toolsUsed), null)));
    }

    private Mono<LlmResponse> step(List<ObjectNode> messages, int depth, List<String> toolsUsed,
                                   String system, Collection<AgentTool> tools) {
        if (depth >= props.maxToolIterations()) {
            return Mono.just(new LlmResponse(
                    "Stopped: exceeded the maximum of " + props.maxToolIterations() + " tool iterations.",
                    List.of(), null, "max_iterations"));
        }
        return llm.chat(system, messages, tools)
                .flatMap(response -> {
                    messages.add(assistantMessage(response));
                    if (!response.wantsTools()) {
                        return Mono.just(response);
                    }
                    return executeTools(response.toolCalls(), toolsUsed)
                            .map(results -> {
                                messages.add(results);
                                return results;
                            })
                            .then(Mono.defer(() -> step(messages, depth + 1, toolsUsed, system, tools)));
                });
    }

    /** Runs all requested tool calls concurrently, preserving call order in the result message. */
    private Mono<ObjectNode> executeTools(List<ToolCall> calls, List<String> toolsUsed) {
        calls.forEach(call -> toolsUsed.add(call.name()));
        return Flux.fromIterable(calls)
                .flatMap(call -> registry.execute(call.name(), call.input())
                        .map(result -> toolResultBlock(call.id(), result)))
                .collectList()
                .map(blocks -> {
                    ObjectNode message = mapper.createObjectNode();
                    message.put("role", "user");
                    ArrayNode content = message.putArray("content");
                    blocks.forEach(content::add);
                    return message;
                });
    }

    /**
     * Records a failed turn as a clean {@code user -> assistant(fallback)} exchange,
     * rolling back any partial tool turns, so a transient failure can't leave a
     * dangling turn that the model API would reject on the next request.
     */
    private String recordFailure(List<ObjectNode> messages, int mark, String userMessage, Throwable e) {
        String text = "I hit an internal error and could not complete that request: " + e.getMessage();
        while (messages.size() > mark) {
            messages.remove(messages.size() - 1);
        }
        messages.add(textMessage("user", userMessage));
        messages.add(textMessage("assistant", text));
        return text;
    }

    private ObjectNode textMessage(String role, String text) {
        ObjectNode message = mapper.createObjectNode();
        message.put("role", role);
        message.put("content", text);
        return message;
    }

    private ObjectNode assistantMessage(LlmResponse response) {
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        message.set("content", response.rawContent());
        return message;
    }

    private ObjectNode toolResultBlock(String toolUseId, String result) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "tool_result");
        block.put("tool_use_id", toolUseId);
        block.put("content", result);
        return block;
    }
}
