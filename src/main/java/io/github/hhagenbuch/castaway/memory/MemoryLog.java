package io.github.hhagenbuch.castaway.memory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session, append-only event log (DESIGN.md sections 2.5, 4). Because memory is a
 * log rather than mutable state, ship&lt;-&gt;shore sync is log shipping + merge, not
 * diffing:
 *
 * <ul>
 *   <li><b>Append</b> assigns a per-session Lamport counter ({@code seq}) tagged with
 *       this node's id, plus a wall clock for the LWW tiebreaker.</li>
 *   <li><b>Sync</b> ships, by per-node high-water mark, the events the other side lacks
 *       ({@link #since}); {@link #merge} unions them, deduping by {@code (nodeId, seq)}.</li>
 *   <li><b>Divergence</b> — both sides appending at the same {@code seq} while
 *       partitioned — is detected, both branches retained, and the visible head resolved
 *       last-writer-wins with a {@link EventType#FOLD_BACK} event carrying the loser as
 *       context, so nothing is silently dropped.</li>
 * </ul>
 */
@Component
public class MemoryLog {

    private final String nodeId;
    private final Clock clock;
    private final Map<String, List<MemoryEvent>> sessions = new ConcurrentHashMap<>();

    public MemoryLog(@Value("${castaway.node-id:local}") String nodeId, Clock clock) {
        this.nodeId = nodeId;
        this.clock = clock;
    }

    public String nodeId() {
        return nodeId;
    }

    /** Append a new event authored by this node; returns it with its assigned seq. */
    public synchronized MemoryEvent append(String sessionId, EventType type, String contentJson) {
        List<MemoryEvent> log = sessions.computeIfAbsent(sessionId, s -> new ArrayList<>());
        long seq = nextSeq(log);
        MemoryEvent event = MemoryEvent.create(sessionId, nodeId, seq, clock.millis(), type, contentJson);
        log.add(event);
        return event;
    }

    /** All events for a session in causal order (seq, then wall clock, then node). */
    public synchronized List<MemoryEvent> events(String sessionId) {
        List<MemoryEvent> log = sessions.get(sessionId);
        if (log == null) {
            return List.of();
        }
        List<MemoryEvent> sorted = new ArrayList<>(log);
        sorted.sort(ORDER);
        return sorted;
    }

    /** Per-node high-water mark: the max seq this node has seen from each origin node. */
    public synchronized Map<String, Long> highWater(String sessionId) {
        Map<String, Long> hw = new HashMap<>();
        for (MemoryEvent e : sessions.getOrDefault(sessionId, List.of())) {
            hw.merge(e.nodeId(), e.seq(), Math::max);
        }
        return hw;
    }

    /** Events the other side lacks, given its high-water marks (what to ship on reconnect). */
    public synchronized List<MemoryEvent> since(String sessionId, Map<String, Long> theirHighWater) {
        List<MemoryEvent> out = new ArrayList<>();
        for (MemoryEvent e : sessions.getOrDefault(sessionId, List.of())) {
            if (e.seq() > theirHighWater.getOrDefault(e.nodeId(), 0L)) {
                out.add(e);
            }
        }
        out.sort(ORDER);
        return out;
    }

    /** Merge incoming events, deduping by (nodeId, seq). Returns how many were new. */
    public synchronized int merge(List<MemoryEvent> incoming) {
        int added = 0;
        for (MemoryEvent e : incoming) {
            List<MemoryEvent> log = sessions.computeIfAbsent(e.sessionId(), s -> new ArrayList<>());
            Set<String> known = new HashSet<>();
            log.forEach(x -> known.add(x.id()));
            if (!known.contains(e.id())) {
                log.add(e);
                added++;
            }
        }
        return added;
    }

    /** Sequence numbers at which two or more distinct nodes appended — the conflicts. */
    public synchronized List<Long> divergentSeqs(String sessionId) {
        Map<Long, Set<String>> nodesAtSeq = new HashMap<>();
        for (MemoryEvent e : sessions.getOrDefault(sessionId, List.of())) {
            nodesAtSeq.computeIfAbsent(e.seq(), k -> new HashSet<>()).add(e.nodeId());
        }
        List<Long> diverged = new ArrayList<>();
        nodesAtSeq.forEach((seq, nodes) -> {
            if (nodes.size() > 1) {
                diverged.add(seq);
            }
        });
        diverged.sort(Comparator.naturalOrder());
        return diverged;
    }

    public synchronized boolean hasDivergence(String sessionId) {
        return !divergentSeqs(sessionId).isEmpty();
    }

    /**
     * Resolve the visible head of the newest divergence: pick the LWW winner (latest
     * wall clock, node id as deterministic tiebreak), keep the losing branch, and append
     * a FOLD_BACK event summarizing it so the model is told what happened elsewhere.
     */
    public synchronized Optional<Resolution> resolveHead(String sessionId, Summarizer summarizer) {
        List<Long> diverged = divergentSeqs(sessionId);
        if (diverged.isEmpty()) {
            return Optional.empty();
        }
        long headSeq = diverged.get(diverged.size() - 1);
        List<MemoryEvent> atHead = new ArrayList<>();
        for (MemoryEvent e : sessions.get(sessionId)) {
            if (e.seq() == headSeq) {
                atHead.add(e);
            }
        }
        MemoryEvent winner = atHead.stream()
                .max(Comparator.comparingLong(MemoryEvent::wallClockMillis).thenComparing(MemoryEvent::nodeId))
                .orElseThrow();
        List<MemoryEvent> losers = atHead.stream().filter(e -> !e.id().equals(winner.id())).toList();
        MemoryEvent foldBack = append(sessionId, EventType.FOLD_BACK, summarizer.summarize(losers));
        return Optional.of(new Resolution(winner, losers, foldBack));
    }

    private long nextSeq(List<MemoryEvent> log) {
        long max = 0;
        for (MemoryEvent e : log) {
            max = Math.max(max, e.seq());
        }
        return max + 1;
    }

    private static final Comparator<MemoryEvent> ORDER = Comparator
            .comparingLong(MemoryEvent::seq)
            .thenComparingLong(MemoryEvent::wallClockMillis)
            .thenComparing(MemoryEvent::nodeId);

    /** Outcome of resolving one divergence. */
    public record Resolution(MemoryEvent winner, List<MemoryEvent> folded, MemoryEvent foldBackEvent) {
    }
}
