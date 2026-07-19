package io.github.hhagenbuch.castaway.outbox;

/**
 * Decides whether a queued action still makes sense now that the link is back —
 * the most original idea in the project (DESIGN.md section 2.4): reconnecting does
 * not blindly replay. An action queued six hours ago ("move the meeting to 3pm")
 * may be stale by the time it can run. An interface so the reconciler can be tested
 * without a model, and so the model-backed implementation can be swapped.
 */
public interface ActionRevalidator {

    Verdict revalidate(OutboxEntry entry);
}
