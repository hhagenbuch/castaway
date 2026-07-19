package io.github.hhagenbuch.castaway.link;

import io.github.hhagenbuch.castaway.config.CastawayProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class LinkMonitorTest {

    // Defaults: requiredConsecutive = 2, degradedRttMillis = 400.
    private final CastawayProperties props = new CastawayProperties(null, null, null, null);
    private final LinkMonitor monitor = new LinkMonitor(() -> Mono.empty(), props);

    @Test
    void startsOnlineOptimistically() {
        assertThat(monitor.state()).isEqualTo(LinkState.ONLINE);
    }

    @Test
    void requiresConsecutiveBadProbesBeforeGoingOffline() {
        monitor.submit(ProbeResult.unreachable());
        assertThat(monitor.state()).isEqualTo(LinkState.ONLINE); // one bad probe: hysteresis holds

        monitor.submit(ProbeResult.unreachable());
        assertThat(monitor.state()).isEqualTo(LinkState.OFFLINE); // second confirms it
    }

    @Test
    void recoversToOnlineAfterConsecutiveGoodProbes() {
        monitor.submit(ProbeResult.unreachable());
        monitor.submit(ProbeResult.unreachable());
        assertThat(monitor.state()).isEqualTo(LinkState.OFFLINE);

        monitor.submit(ProbeResult.reachable(50));
        assertThat(monitor.state()).isEqualTo(LinkState.OFFLINE); // one good probe isn't enough
        monitor.submit(ProbeResult.reachable(50));
        assertThat(monitor.state()).isEqualTo(LinkState.ONLINE);
    }

    @Test
    void highRttButReachableIsDegraded() {
        monitor.submit(ProbeResult.reachable(700)); // > 400ms threshold
        monitor.submit(ProbeResult.reachable(700));
        assertThat(monitor.state()).isEqualTo(LinkState.DEGRADED);
    }

    @Test
    void aSingleBadProbeDoesNotFlapTheState() {
        // ONLINE, then one dropped probe, then good again: must never leave ONLINE.
        monitor.submit(ProbeResult.unreachable());
        monitor.submit(ProbeResult.reachable(50));
        monitor.submit(ProbeResult.reachable(50));
        assertThat(monitor.state()).isEqualTo(LinkState.ONLINE);
    }

    @Test
    void unreachableHintsDriveTheStateLikeProbes() {
        // A cloud call failing (ModelRouter fallback) hints the monitor; enough hints
        // converge it to OFFLINE without waiting for the probe cadence.
        monitor.hintUnreachable();
        assertThat(monitor.state()).isEqualTo(LinkState.ONLINE); // hysteresis still holds
        monitor.hintUnreachable();
        assertThat(monitor.state()).isEqualTo(LinkState.OFFLINE);
    }

    @Test
    void streamReplaysCurrentStateToLateSubscribers() {
        monitor.submit(ProbeResult.unreachable());
        monitor.submit(ProbeResult.unreachable()); // now OFFLINE

        StepVerifier.create(monitor.stream().next())
                .expectNext(LinkState.OFFLINE)
                .verifyComplete();
    }
}
