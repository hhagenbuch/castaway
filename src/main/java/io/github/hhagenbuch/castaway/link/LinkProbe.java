package io.github.hhagenbuch.castaway.link;

import reactor.core.publisher.Mono;

/**
 * Probes the cloud endpoint for reachability and latency. An interface so the
 * {@link LinkMonitor} can be driven by a real HTTP probe in production and by
 * synthetic results in tests (network conditions are fixtures, not accidents).
 */
public interface LinkProbe {

    Mono<ProbeResult> probe();
}
