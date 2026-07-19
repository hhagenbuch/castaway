package io.github.hhagenbuch.castaway.link;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Exposes the link state so a dashboard/demo can render the ONLINE/DEGRADED/OFFLINE
 * banner live — {@code GET /api/link} for the current value, {@code GET /api/link/stream}
 * for an SSE feed of transitions.
 */
@RestController
@RequestMapping("/api/link")
public class LinkController {

    private final LinkMonitor monitor;

    public LinkController(LinkMonitor monitor) {
        this.monitor = monitor;
    }

    @GetMapping
    public Mono<LinkStatus> current() {
        return Mono.just(new LinkStatus(monitor.state()));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<LinkState> stream() {
        return monitor.stream();
    }

    public record LinkStatus(LinkState link) {
    }
}
