/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter),
 * repackaged into castaway per DESIGN.md section 6 (copy for self-containment).
 */
package io.github.hhagenbuch.castaway.llm;

import tools.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * Abstraction over an LLM. The payoff of this interface in castaway is that the
 * {@code ModelRouter}, the cloud client, and the local client are all just
 * {@code LlmClient}s — the agent loop can't tell which one answered.
 *
 * @param system optional system prompt (the CapabilityGate's degradation notice
 *               when offline); {@code null} or blank means none.
 */
public interface LlmClient {

    Mono<LlmResponse> chat(String system, List<ObjectNode> messages, Collection<AgentTool> tools);
}
