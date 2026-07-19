package io.github.hhagenbuch.castaway.tools;

import io.github.hhagenbuch.castaway.tools.impl.CalculatorTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculatorToolTest {

    private final CalculatorTool calc = new CalculatorTool();

    @Test
    void evaluatesBinaryExpressions() {
        assertThat(calc.evaluate("2 + 2")).isEqualTo("4");
        assertThat(calc.evaluate("973 * 481")).isEqualTo("468013");
        assertThat(calc.evaluate("7 / 2")).isEqualTo("3.5");
    }

    @Test
    void rejectsGarbageAndDivisionByZero() {
        assertThatThrownBy(() -> calc.evaluate("banana")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc.evaluate("1 / 0")).isInstanceOf(ArithmeticException.class);
    }
}
