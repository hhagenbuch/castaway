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
 * @param fellBack   true when the primary client failed on a connectivity error and
 *                   the router silently retried on the local model (see {@code ModelRouter})
 */
public record Provenance(String answeredBy, LinkState link, boolean fellBack) {

    public Provenance(String answeredBy, LinkState link) {
        this(answeredBy, link, false);
    }

    /**
     * Compact one-line rendering for the API and UI. A fallback answer is labelled
     * {@code local:qwen3:8b (FALLBACK)} rather than by link state — the monitor still
     * said ONLINE, so reporting the state would hide that the cloud call actually failed.
     */
    public String render() {
        return fellBack ? answeredBy + " (FALLBACK)" : answeredBy + " (" + link + ")";
    }
}
