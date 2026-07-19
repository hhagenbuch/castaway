package io.github.hhagenbuch.castaway.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One immutable entry in a session's log. Identified by {@code (nodeId, seq)}: the
 * originating node and its per-session Lamport-ish counter. Two nodes appending at
 * the same {@code seq} while partitioned is exactly the divergence the sync layer
 * resolves. {@code contentHash} gives integrity and content-level dedupe.
 *
 * @param wallClockMillis originating node's wall clock; the LWW tiebreaker for the visible head
 */
public record MemoryEvent(String sessionId, String nodeId, long seq, long wallClockMillis,
                          EventType type, String contentJson, String contentHash) {

    /** Stable identity within a session: which node produced it, at which sequence. */
    public String id() {
        return nodeId + "#" + seq;
    }

    static MemoryEvent create(String sessionId, String nodeId, long seq, long wallClockMillis,
                              EventType type, String contentJson) {
        return new MemoryEvent(sessionId, nodeId, seq, wallClockMillis, type, contentJson,
                hash(sessionId, nodeId, seq, type, contentJson));
    }

    private static String hash(String sessionId, String nodeId, long seq, EventType type, String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String material = sessionId + "|" + nodeId + "|" + seq + "|" + type + "|" + content;
            return HexFormat.of().formatHex(md.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
