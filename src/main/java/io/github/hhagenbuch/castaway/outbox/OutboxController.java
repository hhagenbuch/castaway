package io.github.hhagenbuch.castaway.outbox;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Read-only view of the outbox, so the demo/operator can watch actions move
 * QUEUED -> EXECUTED | STALE as the link comes and goes.
 */
@RestController
@RequestMapping("/api/outbox")
public class OutboxController {

    private final Outbox outbox;

    public OutboxController(Outbox outbox) {
        this.outbox = outbox;
    }

    @GetMapping
    public Mono<List<OutboxEntry>> all() {
        return Mono.fromSupplier(outbox::all);
    }
}
