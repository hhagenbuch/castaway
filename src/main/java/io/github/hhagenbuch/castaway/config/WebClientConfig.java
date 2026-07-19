package io.github.hhagenbuch.castaway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /** Talks to the Anthropic Messages API; also reused by the link probe for reachability. */
    @Bean
    public WebClient anthropicWebClient(AgentProperties props) {
        return WebClient.builder()
                .baseUrl("https://api.anthropic.com")
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
