package io.github.hhagenbuch.castaway.outbox;

import tools.jackson.databind.ObjectMapper;
import io.github.hhagenbuch.castaway.email.LoggingEmailTransport;
import io.github.hhagenbuch.castaway.link.LinkMonitor;
import io.github.hhagenbuch.castaway.config.CastawayProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcilerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(10_000), ZoneOffset.UTC);
    private final InMemoryOutbox outbox = new InMemoryOutbox(clock);
    private final LoggingEmailTransport transport = new LoggingEmailTransport();
    private final LinkMonitor link = new LinkMonitor(Mono::empty, new CastawayProperties(null, null, null, null));

    private Reconciler reconciler(ActionRevalidator revalidator) {
        return new Reconciler(link, outbox, revalidator, transport, mapper, clock);
    }

    private OutboxEntry queueEmail(long ttlSeconds) {
        return outbox.enqueue("k1", "send_email",
                "{\"to\":\"bob@x.com\",\"subject\":\"Move meeting\",\"body\":\"3pm?\"}",
                "email to bob", ttlSeconds);
    }

    @Test
    void validActionIsRevalidatedThenExecutedExactlyOnce() {
        queueEmail(86_400);
        Reconciler reconciler = reconciler(entry -> Verdict.valid("still makes sense"));

        reconciler.drain();

        assertThat(transport.sent()).hasSize(1);
        assertThat(transport.sent().get(0).to()).isEqualTo("bob@x.com");
        assertThat(outbox.all().get(0).state()).isEqualTo(OutboxState.EXECUTED);

        // second pass must not re-fire: it's no longer QUEUED
        reconciler.drain();
        assertThat(transport.sent()).hasSize(1);
    }

    @Test
    void staleVerdictSurfacesAndDoesNotExecute() {
        queueEmail(86_400);
        Reconciler reconciler = reconciler(entry -> Verdict.stale("the meeting it was moving has passed"));

        reconciler.drain();

        assertThat(transport.sent()).isEmpty();                       // never fired
        assertThat(outbox.all().get(0).state()).isEqualTo(OutboxState.STALE);
    }

    @Test
    void executeFailureOrphansTheEntryAndDoesNotRetry() {
        // An action with no executor (unknown type) fails after REVALIDATED.
        outbox.enqueue("k2", "unknown_action", "{}", "ctx", 86_400);
        Reconciler reconciler = reconciler(entry -> Verdict.valid("looks fine"));

        reconciler.drain();

        assertThat(transport.sent()).isEmpty();
        assertThat(outbox.all().get(0).state()).isEqualTo(OutboxState.ORPHANED);

        // at-most-once: a second pass must not re-attempt it (it's no longer QUEUED)
        reconciler.drain();
        assertThat(outbox.all().get(0).state()).isEqualTo(OutboxState.ORPHANED);
    }

    @Test
    void startupSweepSurfacesEntriesStrandedInRevalidated() {
        // Simulate a crash between REVALIDATED and EXECUTED: an entry left in REVALIDATED.
        OutboxEntry entry = queueEmail(86_400);
        outbox.updateState(entry.id(), OutboxState.REVALIDATED);
        Reconciler reconciler = reconciler(e -> Verdict.valid("unused"));

        reconciler.sweepOrphans();

        assertThat(outbox.all().get(0).state()).isEqualTo(OutboxState.ORPHANED); // surfaced
        assertThat(transport.sent()).isEmpty();                                  // never re-fired
    }

    @Test
    void ttlExpiredIsStaleWithoutEvenRevalidating() {
        queueEmail(60); // created at epoch 10_000, ttl 60s; clock is also 10_000 -> not yet...
        // Re-run reconciler with a clock far in the future so the entry is expired.
        Clock future = Clock.fixed(Instant.ofEpochSecond(10_000 + 3_600), ZoneOffset.UTC);
        boolean[] revalidatorCalled = {false};
        Reconciler reconciler = new Reconciler(link, outbox, entry -> {
            revalidatorCalled[0] = true;
            return Verdict.valid("should not be asked");
        }, transport, mapper, future);

        reconciler.drain();

        assertThat(revalidatorCalled[0]).isFalse();                   // TTL short-circuits revalidation
        assertThat(transport.sent()).isEmpty();
        assertThat(outbox.all().get(0).state()).isEqualTo(OutboxState.STALE);
    }
}
