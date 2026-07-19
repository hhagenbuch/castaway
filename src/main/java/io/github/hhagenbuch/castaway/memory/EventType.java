package io.github.hhagenbuch.castaway.memory;

/** The kinds of immutable events an append-only {@link MemoryLog} records (DESIGN.md section 4). */
public enum EventType {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    TOOL_RESULT,
    ACTION_PROPOSED,
    ACTION_OUTCOME,
    /** Emitted by conflict resolution: folds a divergent branch back in as context. */
    FOLD_BACK
}
