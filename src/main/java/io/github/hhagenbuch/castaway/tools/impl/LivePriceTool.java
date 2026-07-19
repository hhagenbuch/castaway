package io.github.hhagenbuch.castaway.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.tools.AgentTool;
import io.github.hhagenbuch.castaway.tools.LinkRequirement;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The canonical {@code ONLINE_ONLY} tool: live market data has no meaningful offline
 * answer, so the CapabilityGate hides it entirely when the link is down rather than
 * letting the model invent a price. (Fake data — a stand-in for any live feed.)
 */
@Component
public class LivePriceTool implements AgentTool {

    @Override
    public String name() {
        return "live_price";
    }

    @Override
    public String description() {
        return "Returns the current live market price for a ticker symbol. Requires a live connection.";
    }

    @Override
    public LinkRequirement linkRequirement() {
        return LinkRequirement.ONLINE_ONLY;
    }

    @Override
    public ObjectNode inputSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode ticker = properties.putObject("ticker");
        ticker.put("type", "string");
        ticker.put("description", "Ticker symbol, e.g. 'ACME'.");
        schema.putArray("required").add("ticker");
        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        String ticker = input.path("ticker").asText("?").toUpperCase();
        // Stand-in for a real feed; deterministic so it's demo-stable.
        double price = 100 + (Math.abs(ticker.hashCode()) % 9000) / 100.0;
        return Mono.just(ticker + " is trading at $" + String.format("%.2f", price));
    }
}
