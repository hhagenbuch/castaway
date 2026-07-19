package io.github.hhagenbuch.castaway.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.config.CastawayProperties;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The local fallback model, served by Ollama's {@code /api/chat} (an
 * OpenAI-compatible tools format). {@link ModelRouter} reaches for it when
 * {@code OFFLINE} (or {@code DEGRADED} by policy). Responses normalize to the
 * same {@link LlmResponse} as the cloud client so the agent loop can't tell them apart.
 *
 * <p>Small local models are materially worse at tool calling (DESIGN.md section 5);
 * Phase 1 wires the path and normalizes messages, but hardening offline tool
 * execution (strict schemas, retry-on-invalid, a shrunk toolset) is Phase 2's
 * CapabilityGate work. The message conversion below is deliberately best-effort:
 * Anthropic tool_use/tool_result blocks are flattened to text/tool turns.
 */
@Component
public class LocalLlmClient implements LlmClient {

    private final WebClient ollama;
    private final CastawayProperties props;
    private final ObjectMapper mapper;

    public LocalLlmClient(WebClient ollamaWebClient, CastawayProperties props, ObjectMapper mapper) {
        this.ollama = ollamaWebClient;
        this.props = props;
        this.mapper = mapper;
    }

    /** Provenance label for answers this client produces. */
    public String label() {
        return "local:" + props.local().model();
    }

    @Override
    public Mono<LlmResponse> chat(List<ObjectNode> messages, Collection<AgentTool> tools) {
        return ollama.post()
                .uri("/api/chat")
                .bodyValue(buildBody(messages, tools))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parse);
    }

    private ObjectNode buildBody(List<ObjectNode> messages, Collection<AgentTool> tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.local().model());
        body.put("stream", false);
        ObjectNode options = body.putObject("options");
        options.put("num_predict", props.local().maxTokens());
        body.set("messages", toOllamaMessages(messages));
        if (!tools.isEmpty()) {
            ArrayNode toolArray = body.putArray("tools");
            for (AgentTool tool : tools) {
                ObjectNode fn = toolArray.addObject();
                fn.put("type", "function");
                ObjectNode function = fn.putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.set("parameters", tool.inputSchema(mapper));
            }
        }
        return body;
    }

    /** Flattens internal (Anthropic-shaped) messages into Ollama's flat role/content form. */
    private ArrayNode toOllamaMessages(List<ObjectNode> messages) {
        ArrayNode out = mapper.createArrayNode();
        for (ObjectNode message : messages) {
            String role = message.path("role").asText();
            JsonNode content = message.path("content");
            if (content.isTextual()) {
                out.add(msg(role, content.asText()));
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode block : content) {
                switch (block.path("type").asText()) {
                    case "text" -> text.append(block.path("text").asText());
                    case "tool_use" -> text.append("[called tool ").append(block.path("name").asText())
                            .append(" with ").append(block.path("input")).append("]");
                    case "tool_result" -> out.add(msg("tool", block.path("content").asText()));
                    default -> { /* ignore */ }
                }
            }
            if (!text.isEmpty()) {
                out.add(msg(role, text.toString()));
            }
        }
        return out;
    }

    private ObjectNode msg(String role, String content) {
        ObjectNode m = mapper.createObjectNode();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private LlmResponse parse(JsonNode response) {
        JsonNode message = response.path("message");
        String text = message.path("content").asText("");
        List<ToolCall> toolCalls = new ArrayList<>();
        ArrayNode rawContent = mapper.createArrayNode();
        if (!text.isBlank()) {
            ObjectNode textBlock = rawContent.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", text);
        }
        int i = 0;
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            String name = function.path("name").asText();
            JsonNode arguments = function.path("arguments");
            String id = "local_" + (i++);
            toolCalls.add(new ToolCall(id, name, arguments));
            ObjectNode useBlock = rawContent.addObject();
            useBlock.put("type", "tool_use");
            useBlock.put("id", id);
            useBlock.put("name", name);
            useBlock.set("input", arguments);
        }
        String stopReason = toolCalls.isEmpty() ? "end_turn" : "tool_use";
        return new LlmResponse(text, toolCalls, rawContent, stopReason);
    }
}
