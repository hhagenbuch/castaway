/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter),
 * repackaged into castaway per DESIGN.md section 6. Extended with Provenance so the
 * caller learns which model answered and under what link state.
 */
package io.github.hhagenbuch.castaway.core;

import io.github.hhagenbuch.castaway.llm.Provenance;

import java.util.List;

/**
 * Outcome of an agent run: the final answer, the trajectory (tool names in call
 * order), and the {@link Provenance} of the final turn.
 *
 * @param answer     the final text answer
 * @param toolsUsed  tool names invoked during the run, in order (may repeat)
 * @param provenance who answered and under what link state; may be {@code null}
 *                   for a synthetic answer (max-iteration stop, internal error)
 */
public record AgentResult(String answer, List<String> toolsUsed, Provenance provenance) {
}
