/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter)
 * and repackaged into castaway per DESIGN.md section 6 (copy for self-containment).
 * MCP config trimmed — castaway's Phase 1 does not mount MCP servers.
 */
package io.github.hhagenbuch.castaway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cloud-model configuration, bound from the {@code agent.*} namespace. Used by
 * {@code CloudLlmClient} when the link is ONLINE.
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("claude-sonnet-5") String model,
        @DefaultValue("1024") int maxTokens,
        @DefaultValue("6") int maxToolIterations,
        @DefaultValue("3") int maxRetries) {
}
