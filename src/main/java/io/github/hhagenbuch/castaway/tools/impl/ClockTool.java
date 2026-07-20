/*
 * Adapted from spring-ai-agent-starter (github.com/hhagenbuch/spring-ai-agent-starter),
 * repackaged into castaway per DESIGN.md section 6 (copy for self-containment).
 */
package io.github.hhagenbuch.castaway.tools.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Current date/time — grounds the model in reality and works fully offline. */
@Component
public class ClockTool implements AgentTool {

    @Override
    public String name() {
        return "clock";
    }

    @Override
    public String description() {
        return "Returns the current date and time, optionally in a given IANA time zone (default UTC).";
    }

    @Override
    public ObjectNode inputSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode zone = properties.putObject("zone");
        zone.put("type", "string");
        zone.put("description", "IANA zone id, e.g. 'America/New_York'. Defaults to UTC.");
        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        return Mono.fromSupplier(() -> {
            String zone = input.path("zone").asText("UTC");
            return ZonedDateTime.now(ZoneId.of(zone))
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        });
    }
}
