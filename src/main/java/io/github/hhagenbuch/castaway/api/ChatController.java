package io.github.hhagenbuch.castaway.api;

import io.github.hhagenbuch.castaway.core.AgentLoop;
import io.github.hhagenbuch.castaway.core.AgentResult;
import io.github.hhagenbuch.castaway.core.ConversationMemory;
import io.github.hhagenbuch.castaway.llm.Provenance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AgentLoop agentLoop;
    private final ConversationMemory memory;

    public ChatController(AgentLoop agentLoop, ConversationMemory memory) {
        this.agentLoop = agentLoop;
        this.memory = memory;
    }

    @PostMapping
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
                ? request.sessionId()
                : UUID.randomUUID().toString();
        return agentLoop.run(sessionId, request.message())
                .map(result -> new ChatResponse(
                        sessionId, result.answer(), result.toolsUsed(), render(result)));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> reset(@PathVariable String sessionId) {
        memory.clear(sessionId);
        return ResponseEntity.noContent().build();
    }

    private static String render(AgentResult result) {
        Provenance p = result.provenance();
        return p == null ? "unknown" : p.render();
    }
}
