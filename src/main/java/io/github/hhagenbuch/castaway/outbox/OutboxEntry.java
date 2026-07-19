package io.github.hhagenbuch.castaway.outbox;

/**
 * One deferred side-effect. Persisted durably so it survives a restart (the whole
 * point — the link may not return for hours).
 *
 * @param id                surrogate key
 * @param idempotencyKey    content-derived; guards against double-queue and double-fire
 * @param actionType        e.g. {@code send_email}
 * @param payloadJson       the action arguments, as JSON
 * @param context           human/model-readable description of what produced it (for revalidation)
 * @param createdAtEpochSec when it was queued
 * @param ttlSeconds        how long it stays valid; 0 means no expiry
 * @param state             lifecycle position
 */
public record OutboxEntry(long id, String idempotencyKey, String actionType, String payloadJson,
                          String context, long createdAtEpochSec, long ttlSeconds, OutboxState state) {

    /** Expired if a positive TTL has elapsed — revalidation is skipped and it goes STALE. */
    public boolean isExpired(long nowEpochSec) {
        return ttlSeconds > 0 && nowEpochSec > createdAtEpochSec + ttlSeconds;
    }
}
