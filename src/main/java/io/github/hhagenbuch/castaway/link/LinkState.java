package io.github.hhagenbuch.castaway.link;

/**
 * Connectivity to the cloud, as a three-state machine.
 *
 * <p>{@code DEGRADED} is the state that earns the third value: a satellite link
 * is rarely <em>down</em>, it is high-latency and lossy — reachable but expensive
 * to use for long generations. Two states (up/down) would force the router to
 * treat 700&nbsp;ms RTT as either healthy or dead; neither is honest.
 */
public enum LinkState {
    ONLINE,
    DEGRADED,
    OFFLINE
}
