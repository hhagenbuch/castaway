package io.github.hhagenbuch.castaway.tools;

/**
 * How much connectivity a tool needs, so the {@code CapabilityGate} can decide,
 * per link state, whether the model may see and use it (DESIGN.md section 2.3).
 */
public enum LinkRequirement {

    /** Pure / local; always available (calculator, clock, local search). */
    OFFLINE_CAPABLE,

    /** Has a side-effect that can be queued. Offline it stays visible but in *draft*
     *  mode — the model may compose the action, and it is queued for execution on
     *  reconnect rather than run now (send email). */
    DEFERRABLE,

    /** Needs live data; hidden entirely when the link is not {@code ONLINE} (live pricing). */
    ONLINE_ONLY
}
