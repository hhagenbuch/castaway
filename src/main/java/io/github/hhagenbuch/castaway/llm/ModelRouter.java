package io.github.hhagenbuch.castaway.llm;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.config.CastawayProperties;
import io.github.hhagenbuch.castaway.link.LinkMonitor;
import io.github.hhagenbuch.castaway.link.LinkState;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * The payoff of the {@link LlmClient} abstraction: the agent loop injects a
 * single {@code LlmClient}, and this router decides per request which real client
 * answers, based on the live {@link LinkMonitor} state. Every answer is tagged
 * with {@link Provenance} so the caller can see whether it came from the cloud or
 * the local model, and under what link conditions.
 *
 * <ul>
 *   <li>{@code ONLINE}   -> cloud</li>
 *   <li>{@code OFFLINE}  -> local (Ollama)</li>
 *   <li>{@code DEGRADED} -> policy: prefer local by default (latency x tokens is
 *       brutal on a satellite link); flip via {@code castaway.routing.degraded-prefers-local}.
 *       Budget-aware routing (cloud for short high-value reasoning) is a later increment.</li>
 * </ul>
 *
 * <p><b>Call-level fallback.</b> The monitor <em>detects</em> a dead link; it does not
 * <em>protect</em> against one. Between pulling the plug and the hysteresis flipping to
 * OFFLINE (probe-interval x required-consecutive), the state still says ONLINE and cloud
 * calls fail. So when a cloud call fails on a connectivity error, we retry the same
 * request on the local model in-band — tagged {@code (FALLBACK)} — and nudge the monitor
 * toward OFFLINE. This is what lets a conversation survive the plug being pulled mid-turn.
 */
@Component
@Primary
public class ModelRouter implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final LinkMonitor link;
    private final CloudLlmClient cloud;
    private final LocalLlmClient local;
    private final boolean degradedPrefersLocal;

    public ModelRouter(LinkMonitor link, CloudLlmClient cloud, LocalLlmClient local, CastawayProperties props) {
        this.link = link;
        this.cloud = cloud;
        this.local = local;
        this.degradedPrefersLocal = props.routing().degradedPrefersLocal();
    }

    @Override
    public Mono<LlmResponse> chat(String system, List<ObjectNode> messages, Collection<AgentTool> tools) {
        LinkState state = link.state();
        if (route(state) == cloud) {
            // cloud primary -> fall back to local on connectivity failure, and nudge the
            // monitor toward OFFLINE (the plug was likely pulled mid-request).
            return cloud.chat(system, messages, tools)
                    .map(r -> r.withProvenance(new Provenance(cloud.label(), state)))
                    .onErrorResume(ModelRouter::isConnectivity,
                            e -> fallBack(local, local.label(), true, system, messages, tools, state, e));
        }
        // local primary. Fall back to cloud only when the link state says cloud is
        // reachable (DEGRADED) — never when OFFLINE, where the clear "local unavailable"
        // error must surface instead of a doomed cloud retry.
        Mono<LlmResponse> localCall = local.chat(system, messages, tools)
                .map(r -> r.withProvenance(new Provenance(local.label(), state)));
        if (state == LinkState.OFFLINE) {
            return localCall;
        }
        return localCall.onErrorResume(ModelRouter::isConnectivity,
                e -> fallBack(cloud, cloud.label(), false, system, messages, tools, state, e));
    }

    /**
     * The chosen client failed on a connectivity error; retry the same request on the
     * other one, tagged {@code (FALLBACK)} so the user sees it wasn't the normal path.
     *
     * @param hint whether to nudge the monitor toward OFFLINE — true only for the
     *             cloud-&gt;local direction (a failed cloud call is evidence the link is
     *             down); false for local-&gt;cloud (the cloud is up, the local model isn't).
     */
    private Mono<LlmResponse> fallBack(LlmClient target, String label, boolean hint, String system,
                                       List<ObjectNode> messages, Collection<AgentTool> tools,
                                       LinkState state, Throwable cause) {
        log.warn("Primary model failed on connectivity ({}); falling back to {}", cause.toString(), label);
        if (hint) {
            link.hintUnreachable();
        }
        return target.chat(system, messages, tools)
                .map(r -> r.withProvenance(new Provenance(label, state, true)));
    }

    /** Which client answers in this link state. Package-visible for the router test. */
    LlmClient route(LinkState state) {
        return switch (state) {
            case ONLINE -> cloud;
            case OFFLINE -> local;
            case DEGRADED -> degradedPrefersLocal ? local : cloud;
        };
    }

    /**
     * A transport/connectivity failure (link is down) as opposed to an HTTP status
     * error (cloud is reachable but unhappy — {@code WebClientResponseException}, which
     * is deliberately not matched here so we don't mask real 4xx/5xx behind a local answer).
     */
    static boolean isConnectivity(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof WebClientRequestException
                    || c instanceof ConnectException
                    || c instanceof UnknownHostException
                    || c instanceof TimeoutException
                    || c instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
