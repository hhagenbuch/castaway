package io.github.hhagenbuch.castaway.memory;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryLogTest {

    private final String session = "s1";

    private MemoryLog log(String nodeId, long fixedMillis) {
        return new MemoryLog(nodeId, Clock.fixed(Instant.ofEpochMilli(fixedMillis), ZoneOffset.UTC));
    }

    @Test
    void appendAssignsMonotonicPerSessionSeq() {
        MemoryLog ship = log("ship", 100);
        assertThat(ship.append(session, EventType.USER_MESSAGE, "hi").seq()).isEqualTo(1);
        assertThat(ship.append(session, EventType.ASSISTANT_MESSAGE, "hello").seq()).isEqualTo(2);
        assertThat(ship.events(session)).extracting(MemoryEvent::seq).containsExactly(1L, 2L);
    }

    @Test
    void syncShipsOnlyWhatTheOtherSideLacksAndMergeDedupes() {
        MemoryLog ship = log("ship", 100);
        MemoryLog shore = log("shore", 200);
        MemoryEvent base = ship.append(session, EventType.USER_MESSAGE, "hi"); // (ship, 1)
        shore.merge(List.of(base)); // shore now knows the shared prefix

        MemoryEvent shipReply = ship.append(session, EventType.ASSISTANT_MESSAGE, "ship answer"); // (ship, 2)

        // shore asks ship for anything past shore's high-water marks
        List<MemoryEvent> delta = ship.since(session, shore.highWater(session));
        assertThat(delta).containsExactly(shipReply); // not the base it already has

        assertThat(shore.merge(delta)).isEqualTo(1);
        assertThat(shore.merge(delta)).isEqualTo(0); // idempotent: dedupe by (nodeId, seq)
        assertThat(shore.events(session)).extracting(MemoryEvent::id).containsExactly("ship#1", "ship#2");
    }

    @Test
    void divergenceIsDetectedResolvedLwwAndFoldedBack() {
        MemoryLog ship = log("ship", 100);   // ship's wall clock is earlier
        MemoryLog shore = log("shore", 200);  // shore's is later -> shore wins LWW
        MemoryEvent base = ship.append(session, EventType.USER_MESSAGE, "plan the day"); // (ship, 1)
        shore.merge(List.of(base));

        // Partition: both append at seq 2 with different node ids.
        ship.append(session, EventType.ASSISTANT_MESSAGE, "ship: went sailing");   // (ship, 2) @100
        shore.append(session, EventType.ASSISTANT_MESSAGE, "shore: booked dinner"); // (shore, 2) @200

        // Reconnect: ship pulls shore's missing events.
        ship.merge(shore.since(session, ship.highWater(session)));

        assertThat(ship.hasDivergence(session)).isTrue();
        assertThat(ship.divergentSeqs(session)).containsExactly(2L);

        MemoryLog.Resolution res = ship.resolveHead(session, new ConcatSummarizer()).orElseThrow();
        assertThat(res.winner().nodeId()).isEqualTo("shore");        // later wall clock wins
        assertThat(res.folded()).extracting(MemoryEvent::nodeId).containsExactly("ship");
        assertThat(res.foldBackEvent().type()).isEqualTo(EventType.FOLD_BACK);
        assertThat(res.foldBackEvent().contentJson()).contains("went sailing"); // loser kept as context
        assertThat(res.foldBackEvent().seq()).isEqualTo(3L);         // fold-back is the new head
    }
}
