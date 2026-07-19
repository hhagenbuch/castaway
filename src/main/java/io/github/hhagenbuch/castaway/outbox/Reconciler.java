package io.github.hhagenbuch.castaway.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hhagenbuch.castaway.email.EmailTransport;
import io.github.hhagenbuch.castaway.link.LinkMonitor;
import io.github.hhagenbuch.castaway.link.LinkState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.util.List;

/**
 * Drains the {@link Outbox} when the link returns (DESIGN.md sections 2.4 and 3).
 * Subscribes to the monitor's transitions; on ONLINE it walks each QUEUED action:
 * expired-by-TTL goes straight to STALE; otherwise it is revalidated, and only a
 * valid verdict leads to execution. State is advanced to REVALIDATED before the
 * side-effect and EXECUTED after, so a re-run can't fire it twice.
 */
@Component
public class Reconciler {

    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    private final LinkMonitor link;
    private final Outbox outbox;
    private final ActionRevalidator revalidator;
    private final EmailTransport emailTransport;
    private final ObjectMapper mapper;
    private final Clock clock;

    private Disposable subscription;

    public Reconciler(LinkMonitor link, Outbox outbox, ActionRevalidator revalidator,
                      EmailTransport emailTransport, ObjectMapper mapper, Clock clock) {
        this.link = link;
        this.outbox = outbox;
        this.revalidator = revalidator;
        this.emailTransport = emailTransport;
        this.mapper = mapper;
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        // Blocking JDBC + a blocking revalidation call, so drain on boundedElastic.
        // concatMap serializes drains, so two reconnects can't race the same queue.
        subscription = link.stream()
                .filter(state -> state == LinkState.ONLINE)
                .concatMap(state -> Mono.fromRunnable(this::drain)
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("Reconcile pass failed", e);
                            return Mono.empty();
                        }))
                .subscribe();
    }

    /** Package-visible so a test can trigger a pass without wiring the reactive stream. */
    void drain() {
        List<OutboxEntry> pending = outbox.queued();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Link is back; reconciling {} queued action(s)", pending.size());
        long now = clock.instant().getEpochSecond();
        for (OutboxEntry entry : pending) {
            try {
                if (entry.isExpired(now)) {
                    markStale(entry, "TTL expired before the link returned");
                    continue;
                }
                Verdict verdict = revalidator.revalidate(entry);
                if (!verdict.valid()) {
                    markStale(entry, verdict.reason());
                    continue;
                }
                // At-most-once: advance BEFORE the side-effect, so a crash here can't
                // cause a re-fire. If execution itself fails, we can't tell whether the
                // side-effect happened -> ORPHANED for review, never retried.
                outbox.updateState(entry.id(), OutboxState.REVALIDATED);
                try {
                    execute(entry);
                    outbox.updateState(entry.id(), OutboxState.EXECUTED);
                    log.info("Executed queued action id={} ({})", entry.id(), entry.actionType());
                } catch (Exception ex) {
                    outbox.updateState(entry.id(), OutboxState.ORPHANED);
                    log.error("Action id={} revalidated but execution unconfirmed; ORPHANED "
                            + "(at-most-once, not retried)", entry.id(), ex);
                }
            } catch (Exception e) {
                // Failure BEFORE REVALIDATED (e.g. the revalidator threw): leave it QUEUED
                // so the next reconnect retries it.
                log.error("Failed to reconcile action id={} (left QUEUED for retry)", entry.id(), e);
            }
        }
    }

    /**
     * On startup, surface any entries stranded in {@code REVALIDATED} by a crash before
     * execution was confirmed. Per the at-most-once contract we do NOT re-run them; we
     * move them to {@code ORPHANED} and log, so they're visible on {@code GET /api/outbox}
     * for a human to reconcile rather than being invisible forever.
     */
    @EventListener(ApplicationReadyEvent.class)
    void sweepOrphansOnStartup() {
        Mono.fromRunnable(this::sweepOrphans)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Orphan sweep failed", e);
                    return Mono.empty();
                })
                .subscribe();
    }

    void sweepOrphans() {
        List<OutboxEntry> stranded = outbox.byState(OutboxState.REVALIDATED);
        for (OutboxEntry entry : stranded) {
            outbox.updateState(entry.id(), OutboxState.ORPHANED);
            log.warn("Startup: action id={} was stranded in REVALIDATED (crash before execution "
                    + "confirmed); marked ORPHANED for review, NOT retried (at-most-once)", entry.id());
        }
    }

    private void execute(OutboxEntry entry) throws Exception {
        if ("send_email".equals(entry.actionType())) {
            JsonNode p = mapper.readTree(entry.payloadJson());
            emailTransport.send(p.path("to").asText(), p.path("subject").asText(), p.path("body").asText());
            return;
        }
        throw new IllegalStateException("no executor for action type '" + entry.actionType() + "'");
    }

    private void markStale(OutboxEntry entry, String reason) {
        outbox.updateState(entry.id(), OutboxState.STALE);
        log.warn("Queued action id={} is STALE and was NOT executed: {} (surfaced to user)", entry.id(), reason);
    }

    @PreDestroy
    void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
