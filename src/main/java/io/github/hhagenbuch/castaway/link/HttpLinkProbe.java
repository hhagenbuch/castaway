package io.github.hhagenbuch.castaway.link;

import io.github.hhagenbuch.castaway.config.CastawayProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Real reachability probe: a cheap request to the cloud host, timing the
 * round-trip. Any HTTP response (even 401/404) means the link is up — we only
 * care that packets get there and back, not about the status. A connect/timeout
 * error means unreachable. No API key required, so probing is free.
 */
@Component
public class HttpLinkProbe implements LinkProbe {

    private final WebClient webClient;
    private final Duration timeout;

    public HttpLinkProbe(WebClient anthropicWebClient, CastawayProperties props) {
        this.webClient = anthropicWebClient;
        this.timeout = Duration.ofMillis(props.link().probeTimeoutMillis());
    }

    @Override
    public Mono<ProbeResult> probe() {
        long start = System.nanoTime();
        return webClient.get()
                .uri("/")
                .retrieve()
                .toBodilessEntity()
                .timeout(timeout)
                // Any HTTP status (including 4xx) still proves the link is up.
                .onErrorResume(WebClientResponseException.class, e -> Mono.empty())
                .then(Mono.fromSupplier(() -> ProbeResult.reachable(millisSince(start))))
                .onErrorResume(e -> Mono.just(ProbeResult.unreachable()));
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
