package io.github.hhagenbuch.castaway.link;

/**
 * The outcome of a single reachability probe against the cloud endpoint.
 *
 * @param reachable  whether the endpoint answered at all within the timeout
 * @param rttMillis  round-trip time; meaningful only when {@code reachable}
 */
public record ProbeResult(boolean reachable, long rttMillis) {

    public static ProbeResult reachable(long rttMillis) {
        return new ProbeResult(true, rttMillis);
    }

    public static ProbeResult unreachable() {
        return new ProbeResult(false, -1);
    }
}
