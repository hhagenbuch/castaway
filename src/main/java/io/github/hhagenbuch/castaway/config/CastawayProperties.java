package io.github.hhagenbuch.castaway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * castaway runtime configuration, bound from the {@code castaway.*} namespace:
 * the local model, link-detection tuning, and routing policy.
 */
@ConfigurationProperties(prefix = "castaway")
public record CastawayProperties(Local local, Link link, Routing routing, Outbox outbox) {

    public CastawayProperties {
        local = local == null ? new Local(null, null, 1024) : local;
        link = link == null ? new Link(true, 5000, 2000, 400, 2) : link;
        routing = routing == null ? new Routing(true) : routing;
        outbox = outbox == null ? new Outbox(null, 86400) : outbox;
    }

    /** The local fallback model, served by Ollama over its OpenAI-compatible API. */
    public record Local(
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("qwen3:8b") String model,
            @DefaultValue("1024") int maxTokens) {
    }

    /** LinkMonitor tuning: probe cadence, the DEGRADED latency threshold, and hysteresis. */
    public record Link(
            @DefaultValue("true") boolean probeEnabled,
            @DefaultValue("5000") long probeIntervalMillis,
            @DefaultValue("2000") long probeTimeoutMillis,
            @DefaultValue("400") long degradedRttMillis,
            @DefaultValue("2") int requiredConsecutive) {
    }

    /** Routing policy for the ambiguous DEGRADED state. */
    public record Routing(@DefaultValue("true") boolean degradedPrefersLocal) {
    }

    /** Deferred-action outbox: the SQLite file and how long a queued action stays valid. */
    public record Outbox(
            @DefaultValue("castaway-outbox.db") String path,
            @DefaultValue("86400") long defaultTtlSeconds) {
    }
}
