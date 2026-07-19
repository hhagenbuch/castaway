/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter),
 * repackaged into castaway per DESIGN.md section 6 (copy for self-containment).
 */
package io.github.hhagenbuch.castaway.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers all {@link AgentTool} beans and dispatches tool calls.
 * Unknown tools and tool failures are converted to error strings rather than
 * propagated — the model should see the failure and recover, not crash the loop.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> discovered) {
        discovered.forEach(t -> tools.put(t.name(), t));
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }

    /** Registers a tool at runtime; a later registration shadowing an existing name is logged. */
    public void register(AgentTool tool) {
        if (tools.containsKey(tool.name())) {
            log.warn("Tool '{}' is being overridden by a later registration", tool.name());
        }
        tools.put(tool.name(), tool);
    }

    public Mono<String> execute(String name, JsonNode input) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return Mono.just("ERROR: unknown tool '" + name + "'");
        }
        return tool.execute(input)
                .onErrorResume(e -> Mono.just("ERROR: tool '" + name + "' failed: " + e.getMessage()));
    }
}
