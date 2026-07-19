package io.github.hhagenbuch.castaway.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxTest {

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC);

    private JdbcOutbox openOutbox(Path dbFile) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbFile);
        JdbcOutbox outbox = new JdbcOutbox(new JdbcTemplate(ds), clock);
        outbox.init();
        return outbox;
    }

    @Test
    void persistsQueriesAndUpdatesState(@TempDir Path tmp) {
        JdbcOutbox outbox = openOutbox(tmp.resolve("outbox.db"));

        OutboxEntry entry = outbox.enqueue("key-1", "send_email", "{\"to\":\"a@b.com\"}", "ctx", 3600);
        assertThat(entry.id()).isPositive();
        assertThat(entry.state()).isEqualTo(OutboxState.QUEUED);
        assertThat(entry.createdAtEpochSec()).isEqualTo(1_000);

        assertThat(outbox.queued()).extracting(OutboxEntry::idempotencyKey).containsExactly("key-1");
        assertThat(outbox.findByIdempotencyKey("key-1")).isPresent();

        outbox.updateState(entry.id(), OutboxState.EXECUTED);
        assertThat(outbox.queued()).isEmpty();
        assertThat(outbox.all()).singleElement()
                .extracting(OutboxEntry::state).isEqualTo(OutboxState.EXECUTED);
    }

    @Test
    void enqueueIsIdempotentOnKey(@TempDir Path tmp) {
        JdbcOutbox outbox = openOutbox(tmp.resolve("outbox.db"));

        OutboxEntry first = outbox.enqueue("dup", "send_email", "{}", "ctx", 3600);
        OutboxEntry second = outbox.enqueue("dup", "send_email", "{}", "ctx", 3600);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(outbox.all()).hasSize(1);
    }

    @Test
    void survivesReopeningTheSameFile(@TempDir Path tmp) {
        Path db = tmp.resolve("outbox.db");
        openOutbox(db).enqueue("persist", "send_email", "{}", "ctx", 3600);

        // A fresh instance on the same file — as if the process restarted.
        JdbcOutbox reopened = openOutbox(db);
        assertThat(reopened.all()).extracting(OutboxEntry::idempotencyKey).containsExactly("persist");
    }
}
