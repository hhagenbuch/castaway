package io.github.hhagenbuch.castaway.outbox;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.llm.CloudLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Revalidates a queued action with the cloud model — which is reachable again by
 * definition, since revalidation only runs on reconnect. Asks the model whether the
 * action, given how much time has passed and its original context, is still sensible
 * or has gone stale, and requires a strict JSON verdict.
 *
 * <p>Conservative on failure: if the check can't be made (no key, transient error,
 * unparseable reply), the action is ruled <em>stale</em> and surfaced rather than
 * fired — an unsent email is recoverable; a wrong one that was auto-sent is not.
 */
@Component
public class LlmActionRevalidator implements ActionRevalidator {

    private static final Logger log = LoggerFactory.getLogger(LlmActionRevalidator.class);

    private final CloudLlmClient cloud;
    private final ObjectMapper mapper;
    private final Clock clock;

    public LlmActionRevalidator(CloudLlmClient cloud, ObjectMapper mapper, Clock clock) {
        this.cloud = cloud;
        this.mapper = mapper;
        this.clock = clock;
    }

    private static final String SYSTEM = """
            You revalidate a side-effect that a user's assistant queued while it was offline.
            Decide whether it is STILL appropriate to execute now, or whether elapsed time has
            made it stale (e.g. it references a meeting, deadline, or time that has since passed).
            Reply with ONLY a JSON object: {"valid": true|false, "reason": "<one sentence>"}.
            """;

    @Override
    public Verdict revalidate(OutboxEntry entry) {
        long now = clock.instant().getEpochSecond();
        long ageMinutes = Math.max(0, (now - entry.createdAtEpochSec()) / 60);
        String prompt = """
                Queued action:
                - type: %s
                - queued_at_epoch_s: %d
                - now_epoch_s: %d
                - age_minutes: %d
                - details: %s
                - context: %s
                Is it still appropriate to execute, or is it stale?
                """.formatted(entry.actionType(), entry.createdAtEpochSec(), now, ageMinutes,
                entry.payloadJson(), entry.context());

        try {
            ObjectNode userMessage = mapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            String text = cloud.chat(SYSTEM, List.of(userMessage), List.of())
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .map(r -> r.text())
                    .orElse("");
            JsonNode verdict = mapper.readTree(extractJson(text));
            return new Verdict(verdict.path("valid").asBoolean(false),
                    verdict.path("reason").asText("revalidated"));
        } catch (Exception e) {
            log.warn("Revalidation failed for action id={}; treating as stale ({})", entry.id(), e.toString());
            return Verdict.stale("could not revalidate against current state: " + e.getMessage());
        }
    }

    /** Tolerates a model that wraps the JSON in prose or code fences. */
    static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("no JSON object in revalidation output: " + text);
        }
        return text.substring(start, end + 1);
    }
}
