/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter),
 * repackaged into castaway per DESIGN.md section 6. Extended with a Provenance
 * field so every turn carries where it was answered (cloud vs local, link state).
 */
package io.github.hhagenbuch.castaway.llm;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Normalized model response, produced identically by the cloud and local clients.
 *
 * @param text        concatenated text blocks (may be empty when the model only calls tools)
 * @param toolCalls   tool invocations the model requested this turn
 * @param rawContent  the raw {@code content} array replayed verbatim as the assistant message
 * @param stopReason  the stop reason ({@code end_turn}, {@code tool_use}, ...)
 * @param provenance  who answered and under what link state; {@code null} until the router tags it
 */
public record LlmResponse(String text, List<ToolCall> toolCalls, JsonNode rawContent,
                          String stopReason, Provenance provenance) {

    /** Convenience for clients that don't set provenance themselves — the router tags it. */
    public LlmResponse(String text, List<ToolCall> toolCalls, JsonNode rawContent, String stopReason) {
        this(text, toolCalls, rawContent, stopReason, null);
    }

    public boolean wantsTools() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** Returns a copy tagged with the given provenance (records are immutable). */
    public LlmResponse withProvenance(Provenance p) {
        return new LlmResponse(text, toolCalls, rawContent, stopReason, p);
    }
}
