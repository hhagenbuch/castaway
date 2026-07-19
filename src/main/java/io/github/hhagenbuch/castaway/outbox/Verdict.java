package io.github.hhagenbuch.castaway.outbox;

/**
 * The result of revalidating a queued action against current state.
 *
 * @param valid  whether it is still appropriate to execute
 * @param reason one-sentence justification (shown to the user when an action is ruled stale)
 */
public record Verdict(boolean valid, String reason) {

    public static Verdict valid(String reason) {
        return new Verdict(true, reason);
    }

    public static Verdict stale(String reason) {
        return new Verdict(false, reason);
    }
}
