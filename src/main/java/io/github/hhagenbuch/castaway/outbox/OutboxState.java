package io.github.hhagenbuch.castaway.outbox;

/**
 * Lifecycle of a deferred action (DESIGN.md section 3):
 *
 * <pre>
 * QUEUED --> REVALIDATED --> EXECUTED
 *   |             |
 *   |             +--> STALE   (revalidation says it no longer makes sense)
 *   +----------------> STALE   (TTL expired before reconnect)
 *   (REJECTED: declined at draft time)
 * </pre>
 */
public enum OutboxState {
    QUEUED,
    REVALIDATED,
    EXECUTED,
    STALE,
    REJECTED
}
