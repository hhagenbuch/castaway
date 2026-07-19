package io.github.hhagenbuch.castaway.outbox;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link Outbox} double so the tool and reconciler tests need no database. */
class InMemoryOutbox implements Outbox {

    private final Clock clock;
    private final Map<Long, OutboxEntry> byId = new LinkedHashMap<>();
    private final Map<String, Long> byKey = new HashMap<>();
    private long seq = 0;

    InMemoryOutbox(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OutboxEntry enqueue(String idempotencyKey, String actionType, String payloadJson,
                               String context, long ttlSeconds) {
        Long existing = byKey.get(idempotencyKey);
        if (existing != null) {
            return byId.get(existing);
        }
        long id = ++seq;
        OutboxEntry entry = new OutboxEntry(id, idempotencyKey, actionType, payloadJson, context,
                clock.instant().getEpochSecond(), ttlSeconds, OutboxState.QUEUED);
        byId.put(id, entry);
        byKey.put(idempotencyKey, id);
        return entry;
    }

    @Override
    public List<OutboxEntry> queued() {
        return byState(OutboxState.QUEUED);
    }

    @Override
    public List<OutboxEntry> byState(OutboxState state) {
        List<OutboxEntry> out = new ArrayList<>();
        for (OutboxEntry e : byId.values()) {
            if (e.state() == state) {
                out.add(e);
            }
        }
        return out;
    }

    @Override
    public List<OutboxEntry> all() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public Optional<OutboxEntry> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(byKey.get(idempotencyKey)).map(byId::get);
    }

    @Override
    public void updateState(long id, OutboxState state) {
        OutboxEntry e = byId.get(id);
        byId.put(id, new OutboxEntry(e.id(), e.idempotencyKey(), e.actionType(), e.payloadJson(),
                e.context(), e.createdAtEpochSec(), e.ttlSeconds(), state));
    }
}
