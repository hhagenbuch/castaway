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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic arithmetic for the model. Pure and offline-capable — the kind of
 * tool that stays available when the link drops.
 */
@Component
public class CalculatorTool implements AgentTool {

    private static final Pattern BINARY =
            Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "Evaluates a simple arithmetic expression of the form 'a op b' where op is one of + - * /.";
    }

    @Override
    public ObjectNode inputSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode expression = properties.putObject("expression");
        expression.put("type", "string");
        expression.put("description", "Expression to evaluate, e.g. '2 + 2'");
        schema.putArray("required").add("expression");
        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        return Mono.fromSupplier(() -> evaluate(input.path("expression").asText()));
    }

    public String evaluate(String expression) {
        Matcher m = BINARY.matcher(expression);
        if (!m.matches()) {
            throw new IllegalArgumentException("unsupported expression: " + expression);
        }
        double a = Double.parseDouble(m.group(1));
        double b = Double.parseDouble(m.group(3));
        double result = switch (m.group(2)) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> {
                if (b == 0) throw new ArithmeticException("division by zero");
                yield a / b;
            }
            default -> throw new IllegalStateException();
        };
        return result == Math.rint(result) && !Double.isInfinite(result)
                ? String.valueOf((long) result)
                : String.valueOf(result);
    }
}
