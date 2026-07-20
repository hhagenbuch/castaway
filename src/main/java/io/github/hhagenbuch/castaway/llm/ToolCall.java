/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter),
 * repackaged into castaway per DESIGN.md section 6 (copy for self-containment).
 */
package io.github.hhagenbuch.castaway.llm;

import tools.jackson.databind.JsonNode;

/** A single tool invocation requested by the model. */
public record ToolCall(String id, String name, JsonNode input) {
}
