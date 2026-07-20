/*
 * Adapted from spring-ai-agent-starter's AnthropicClient
 * (github.com/hhagenbuch/spring-ai-agent-starter), repackaged into castaway per
 * DESIGN.md section 6. Streaming trimmed; this is the ONLINE delegate behind ModelRouter.
 */
package io.github.hhagenbuch.castaway.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.config.AgentProperties;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The cloud model (Anthropic Messages API), with exponential backoff on 429/5xx.
 * Not {@code @Primary}: it is a delegate {@link ModelRouter} reaches for when
 * {@code ONLINE} (or {@code DEGRADED} by policy).
 */
@Component
public class CloudLlmClient implements LlmClient {

    private final WebClient webClient;
    private final AgentProperties props;
    private final ObjectMapper mapper;

    public CloudLlmClient(WebClient anthropicWebClient, AgentProperties props, ObjectMapper mapper) {
        this.webClient = anthropicWebClient;
        this.props = props;
        this.mapper = mapper;
    }

    /** Provenance label for answers this client produces. */
    public String label() {
        return "cloud:" + props.model();
    }

    @Override
    public Mono<LlmResponse> chat(String system, List<ObjectNode> messages, Collection<AgentTool> tools) {
        return webClient.post()
                .uri("/v1/messages")
                .bodyValue(buildBody(system, messages, tools))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parse)
                .retryWhen(Retry.backoff(props.maxRetries(), Duration.ofMillis(500))
                        .filter(CloudLlmClient::isRetryable));
    }

    private ObjectNode buildBody(String system, List<ObjectNode> messages, Collection<AgentTool> tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.model());
        body.put("max_tokens", props.maxTokens());
        if (system != null && !system.isBlank()) {
            body.put("system", system); // Anthropic's top-level system field
        }
        ArrayNode msgs = body.putArray("messages");
        messages.forEach(msgs::add);
        if (!tools.isEmpty()) {
            ArrayNode toolArray = body.putArray("tools");
            for (AgentTool tool : tools) {
                ObjectNode t = toolArray.addObject();
                t.put("name", tool.name());
                t.put("description", tool.description());
                t.set("input_schema", tool.inputSchema(mapper));
            }
        }
        return body;
    }

    private LlmResponse parse(JsonNode response) {
        JsonNode content = response.path("content");
        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode block : content) {
            switch (block.path("type").asText()) {
                case "text" -> text.append(block.path("text").asText());
                case "tool_use" -> toolCalls.add(new ToolCall(
                        block.path("id").asText(),
                        block.path("name").asText(),
                        block.path("input")));
                default -> { /* ignore unknown block types */ }
            }
        }
        return new LlmResponse(text.toString(), toolCalls, content, response.path("stop_reason").asText());
    }

    private static boolean isRetryable(Throwable t) {
        if (t instanceof WebClientResponseException e) {
            return e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError();
        }
        return false;
    }
}
