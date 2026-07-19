package io.github.hhagenbuch.castaway.link;

import io.github.hhagenbuch.castaway.config.CastawayProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * The link state machine (DESIGN.md section 2.1). Active probes are classified
 * ONLINE / DEGRADED / OFFLINE by reachability and RTT; a transition only fires
 * after {@code requiredConsecutive} agreeing probes, so a single slow or dropped
 * probe can't flap the state. Transitions are published on a replayed stream that
 * the router reads (via {@link #state()}) and the demo/dashboard subscribes to
 * (via {@link #stream()}).
 *
 * <p>Starts {@code ONLINE} (optimistic): degrade when the evidence says so.
 */
@Component
public class LinkMonitor {

    private static final Logger log = LoggerFactory.getLogger(LinkMonitor.class);

    private final LinkProbe probe;
    private final CastawayProperties.Link cfg;

    // Replay the latest state so late subscribers (a browser opening the SSE
    // stream, the router asking on the first request) immediately see current.
    private final Sinks.Many<LinkState> sink = Sinks.many().replay().latest();

    private volatile LinkState current = LinkState.ONLINE;
    private LinkState pending = LinkState.ONLINE;
    private int pendingCount = 0;

    private Disposable probing;

    public LinkMonitor(LinkProbe probe, CastawayProperties props) {
        this.probe = probe;
        this.cfg = props.link();
        sink.tryEmitNext(current);
    }

    public LinkState state() {
        return current;
    }

    /** Live transitions, starting with the current state. */
    public Flux<LinkState> stream() {
        return sink.asFlux();
    }

    /** Begin periodic probing once the app is up (skipped when disabled, e.g. in tests). */
    @EventListener(ApplicationReadyEvent.class)
    public void startProbing() {
        if (!cfg.probeEnabled()) {
            log.info("Link probing disabled; link pinned at {}", current);
            return;
        }
        probing = Flux.interval(Duration.ofMillis(cfg.probeIntervalMillis()))
                .concatMap(tick -> probe.probe().onErrorReturn(ProbeResult.unreachable()))
                .subscribe(this::submit);
        log.info("Link probing started ({}ms interval, DEGRADED above {}ms RTT)",
                cfg.probeIntervalMillis(), cfg.degradedRttMillis());
    }

    /**
     * Feed one probe result into the state machine. Package-visible so tests can
     * drive transitions deterministically without any real network.
     */
    synchronized void submit(ProbeResult result) {
        LinkState observed = classify(result);
        if (observed == current) {
            pendingCount = 0;              // stability confirmed; cancel any pending flip
            pending = current;
            return;
        }
        if (observed == pending) {
            pendingCount++;
        } else {
            pending = observed;
            pendingCount = 1;
        }
        if (pendingCount >= cfg.requiredConsecutive()) {
            transition(observed);
        }
    }

    private LinkState classify(ProbeResult result) {
        if (!result.reachable()) {
            return LinkState.OFFLINE;
        }
        return result.rttMillis() > cfg.degradedRttMillis() ? LinkState.DEGRADED : LinkState.ONLINE;
    }

    private void transition(LinkState next) {
        LinkState from = current;
        current = next;
        pending = next;
        pendingCount = 0;
        log.info("Link transition {} -> {}", from, next);
        sink.tryEmitNext(next);
    }

    @PreDestroy
    void stop() {
        if (probing != null) {
            probing.dispose();
        }
    }
}
