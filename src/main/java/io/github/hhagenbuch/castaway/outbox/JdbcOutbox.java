package io.github.hhagenbuch.castaway.outbox;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed {@link Outbox} (embedded, no server, survives restart — DESIGN.md
 * section 2.4). Deliberately plain JDBC via {@link JdbcTemplate}; the queue is small
 * and the durability guarantee is what matters.
 */
@Component
public class JdbcOutbox implements Outbox {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcOutbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS outbox (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    idempotency_key TEXT    NOT NULL UNIQUE,
                    action_type     TEXT    NOT NULL,
                    payload         TEXT    NOT NULL,
                    context         TEXT,
                    created_at      INTEGER NOT NULL,
                    ttl_seconds     INTEGER NOT NULL,
                    state           TEXT    NOT NULL
                )
                """);
    }

    @Override
    public OutboxEntry enqueue(String idempotencyKey, String actionType, String payloadJson,
                               String context, long ttlSeconds) {
        Optional<OutboxEntry> existing = findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get(); // idempotent: same content queued twice stays once
        }
        long createdAt = clock.instant().getEpochSecond();
        jdbc.update("""
                INSERT INTO outbox (idempotency_key, action_type, payload, context, created_at, ttl_seconds, state)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, idempotencyKey, actionType, payloadJson, context, createdAt, ttlSeconds, OutboxState.QUEUED.name());
        return findByIdempotencyKey(idempotencyKey).orElseThrow();
    }

    @Override
    public List<OutboxEntry> queued() {
        return byState(OutboxState.QUEUED);
    }

    @Override
    public List<OutboxEntry> byState(OutboxState state) {
        return jdbc.query("SELECT * FROM outbox WHERE state = ? ORDER BY id", MAPPER, state.name());
    }

    @Override
    public List<OutboxEntry> all() {
        return jdbc.query("SELECT * FROM outbox ORDER BY id", MAPPER);
    }

    @Override
    public Optional<OutboxEntry> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query("SELECT * FROM outbox WHERE idempotency_key = ?", MAPPER, idempotencyKey)
                .stream().findFirst();
    }

    @Override
    public void updateState(long id, OutboxState state) {
        jdbc.update("UPDATE outbox SET state = ? WHERE id = ?", state.name(), id);
    }

    private static final RowMapper<OutboxEntry> MAPPER = (rs, rowNum) -> new OutboxEntry(
            rs.getLong("id"),
            rs.getString("idempotency_key"),
            rs.getString("action_type"),
            rs.getString("payload"),
            rs.getString("context"),
            rs.getLong("created_at"),
            rs.getLong("ttl_seconds"),
            OutboxState.valueOf(rs.getString("state")));
}
