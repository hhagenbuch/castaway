package io.github.hhagenbuch.castaway.capability;

import io.github.hhagenbuch.castaway.link.LinkState;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Rewrites the toolset the model sees, per link state, and injects a system-prompt
 * notice so the agent <em>knows</em> it is degraded and negotiates scope with the
 * user instead of failing or lying (DESIGN.md section 2.3 — the novel UX).
 *
 * <ul>
 *   <li>{@code ONLINE} — every tool, no notice.</li>
 *   <li>otherwise — {@code OFFLINE_CAPABLE} tools stay; {@code DEFERRABLE} tools stay
 *       but in draft mode (the model may compose, not execute — the tool queues it);
 *       {@code ONLINE_ONLY} tools are hidden. A notice tells the model all of this.</li>
 * </ul>
 */
@Component
public class CapabilityGate {

    /** The gated view for one request: the tools the model may see, and the notice (or null). */
    public record Gated(List<AgentTool> tools, String systemNotice) {
    }

    public Gated gate(LinkState state, Collection<AgentTool> all) {
        if (state == LinkState.ONLINE) {
            return new Gated(List.copyOf(all), null);
        }
        List<AgentTool> visible = new ArrayList<>();
        List<String> deferrable = new ArrayList<>();
        List<String> hidden = new ArrayList<>();
        for (AgentTool tool : all) {
            switch (tool.linkRequirement()) {
                case OFFLINE_CAPABLE -> visible.add(tool);
                case DEFERRABLE -> {
                    visible.add(tool);
                    deferrable.add(tool.name());
                }
                case ONLINE_ONLY -> hidden.add(tool.name());
            }
        }
        return new Gated(visible, notice(state, deferrable, hidden));
    }

    private String notice(LinkState state, List<String> deferrable, List<String> hidden) {
        StringBuilder sb = new StringBuilder();
        sb.append("CONNECTIVITY: you are currently ").append(state)
          .append(" and running on a local model, not the cloud. Be honest about this with the user.");
        if (!deferrable.isEmpty()) {
            sb.append(" These tools have real side-effects you CANNOT execute right now: ")
              .append(String.join(", ", deferrable))
              .append(". You may DRAFT such an action; calling the tool will QUEUE it to run automatically when the"
                      + " connection returns. Never tell the user the action is done — say it is queued.");
        }
        if (!hidden.isEmpty()) {
            sb.append(" These tools need a live connection and are unavailable right now: ")
              .append(String.join(", ", hidden)).append(".");
        }
        return sb.toString();
    }
}
