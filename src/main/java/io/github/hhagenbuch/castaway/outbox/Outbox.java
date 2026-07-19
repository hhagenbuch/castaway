package io.github.hhagenbuch.castaway.outbox;

import java.util.List;
import java.util.Optional;

/**
 * Durable queue of deferred side-effects. An interface so the reconciler and the
 * deferrable tools depend on the contract, not the SQLite implementation (and so
 * tests can drive an in-memory double).
 */
public interface Outbox {

    /**
     * Enqueue an action, or return the existing entry if one with the same
     * idempotency key is already present (drafting the same email twice offline
     * must not queue it twice).
     */
    OutboxEntry enqueue(String idempotencyKey, String actionType, String payloadJson,
                        String context, long ttlSeconds);

    /** Entries still awaiting reconciliation ({@code QUEUED}), oldest first. */
    List<OutboxEntry> queued();

    /** Entries in a given state, oldest first — used by the startup orphan sweep. */
    List<OutboxEntry> byState(OutboxState state);

    /** Everything, any state — for the operator view / demo. */
    List<OutboxEntry> all();

    Optional<OutboxEntry> findByIdempotencyKey(String idempotencyKey);

    void updateState(long id, OutboxState state);
}
