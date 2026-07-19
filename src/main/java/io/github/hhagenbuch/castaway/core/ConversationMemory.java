/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter),
 * repackaged into castaway per DESIGN.md section 6 (copy for self-containment).
 * NOTE: Phase 3 replaces this with an append-only MemoryLog for ship<->shore sync
 * (DESIGN.md sections 2.5 and 4); this in-memory version is the Phase 1 placeholder.
 */
package io.github.hhagenbuch.castaway.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory, per-session message history. Simplest thing that works for Phase 1. */
@Component
public class ConversationMemory {

    private final Map<String, List<ObjectNode>> sessions = new ConcurrentHashMap<>();

    public List<ObjectNode> history(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new CopyOnWriteArrayList<>());
    }

    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }
}
