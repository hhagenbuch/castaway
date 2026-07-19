package io.github.hhagenbuch.castaway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;

@Configuration
public class WebClientConfig {

    /** Injectable clock so TTL/staleness logic is testable with a fixed time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Talks to the Anthropic Messages API; also reused by the link probe for reachability.
     * The base URL is overridable ({@code castaway.cloud.base-url}) so the chaos harness can
     * route both probing and cloud calls through a Toxiproxy upstream.
     */
    @Bean
    public WebClient anthropicWebClient(AgentProperties props,
                                        @Value("${castaway.cloud.base-url:https://api.anthropic.com}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", props.apiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    /** Talks to the local Ollama server (OpenAI-compatible chat endpoint). */
    @Bean
    public WebClient ollamaWebClient(CastawayProperties props) {
        return WebClient.builder()
                .baseUrl(props.local().baseUrl())
                .defaultHeader("content-type", "application/json")
                .build();
    }
}
