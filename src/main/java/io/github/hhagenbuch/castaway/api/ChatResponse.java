package io.github.hhagenbuch.castaway.api;

import java.util.List;

/**
 * @param sessionId  conversation id (echoed so the caller can continue the session)
 * @param reply      the agent's final answer
 * @param toolsUsed  tool names the agent invoked this turn, in call order
 * @param provenance who answered and under what link state, e.g.
 *                   {@code local:qwen3:8b (OFFLINE)} — the honesty signal, surfaced
 *                   on every response rather than hidden in a log
 */
public record ChatResponse(String sessionId, String reply, List<String> toolsUsed, String provenance) {
}
