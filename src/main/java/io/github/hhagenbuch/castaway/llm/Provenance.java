package io.github.hhagenbuch.castaway.llm;

import io.github.hhagenbuch.castaway.link.LinkState;

/**
 * Where an answer came from and under what link conditions — surfaced to the
 * user, not buried in a log. Honesty about degradation is castaway's core design
 * principle (DESIGN.md section 1): the user must be able to tell a local, offline
 * answer from a cloud one.
 *
 * @param answeredBy the model/client that produced the answer, e.g. {@code cloud:claude-sonnet-5}
 * @param link       the link state at the time of the call
 */
public record Provenance(String answeredBy, LinkState link) {

    /** Compact one-line rendering for the API and UI, e.g. {@code local:qwen3:8b (OFFLINE)}. */
    public String render() {
        return answeredBy + " (" + link + ")";
    }
}
