package io.github.hhagenbuch.castaway.outbox;

/**
 * Lifecycle of a deferred action (DESIGN.md section 3):
 *
 * <pre>
 * QUEUED --> REVALIDATED --> EXECUTED
 *   |             |    \
 *   |             |     +-> ORPHANED  (crashed/failed after REVALIDATED, before EXECUTED
 *   |             |                     confirmed — execution unknown; surfaced, NOT retried)
 *   |             +--> STALE   (revalidation says it no longer makes sense)
 *   +----------------> STALE   (TTL expired before reconnect)
 *   (REJECTED: declined at draft time)
 * </pre>
 *
 * <p>The state is advanced to {@code REVALIDATED} <em>before</em> the side-effect, which
 * makes execution <b>at-most-once</b>: if the process dies mid-fire we cannot know whether
 * the side-effect happened, so we never re-run it — an unsent email is recoverable, a
 * double-sent one is not. Such entries land in {@code ORPHANED} for human review.
 */
public enum OutboxState {
    QUEUED,
    REVALIDATED,
    EXECUTED,
    STALE,
    ORPHANED,
    REJECTED
}
