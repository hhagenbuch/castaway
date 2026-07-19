/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter),
 * repackaged into castaway per DESIGN.md section 6 (copy for self-containment).
 */
package io.github.hhagenbuch.castaway.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Mono;

/**
 * A capability the agent can invoke. Implementations must be side-effect
 * aware: anything destructive should be idempotent or confirm-gated.
 */
public interface AgentTool {

    String name();

    String description();

    /** JSON Schema describing the expected input object. */
    ObjectNode inputSchema(ObjectMapper mapper);

    /** Execute with validated input; return a plain-text result for the model. */
    Mono<String> execute(JsonNode input);

    /**
     * How much connectivity this tool needs; the {@code CapabilityGate} uses it to
     * hide or defer the tool when the link is down. Defaults to {@code OFFLINE_CAPABLE}
     * — a pure tool is the safe assumption; anything with a side-effect must opt in.
     */
    default LinkRequirement linkRequirement() {
        return LinkRequirement.OFFLINE_CAPABLE;
    }
}
