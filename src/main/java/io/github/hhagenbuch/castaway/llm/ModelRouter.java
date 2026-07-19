package io.github.hhagenbuch.castaway.llm;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.config.CastawayProperties;
import io.github.hhagenbuch.castaway.link.LinkMonitor;
import io.github.hhagenbuch.castaway.link.LinkState;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

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
 */
@Component
@Primary
public class ModelRouter implements LlmClient {

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
    public Mono<LlmResponse> chat(List<ObjectNode> messages, Collection<AgentTool> tools) {
        LinkState state = link.state();
        LlmClient delegate = route(state);
        String label = delegate == cloud ? cloud.label() : local.label();
        return delegate.chat(messages, tools)
                .map(response -> response.withProvenance(new Provenance(label, state)));
    }

    /** Which client answers in this link state. Package-visible for the router test. */
    LlmClient route(LinkState state) {
        return switch (state) {
            case ONLINE -> cloud;
            case OFFLINE -> local;
            case DEGRADED -> degradedPrefersLocal ? local : cloud;
        };
    }
}
