/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package stroom.gwt.workbench.framework.client.play;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestValueExpectation {

    @Test
    void testPassAndFail() {
        assertPasses("abc", e -> e.toBe("abc"));
        assertFails("abc", e -> e.toBe("abd"), "expect(received).toBe(expected)\n\n"
                                                + "Expected: \"abd\"\nReceived: \"abc\"");
        assertPasses("abc", e -> e.not().toBe("abd"));
        assertFails("abc", e -> e.not().toBe("abc"), "expect(received).not.toBe(expected)\n\n"
                                                      + "Expected: not \"abc\"\nReceived: \"abc\"");
        assertPasses(2, e -> e.toBe(2L));
        assertPasses(List.of(1, 3), e -> e.toEqual(List.of(1, 3)));
        assertFails(List.of(1, 3), e -> e.toEqual(List.of(3, 1)), "Received: [1, 3]");
        assertPasses("The Query", e -> e.toContain("Query"));
        assertPasses(List.of("a", "b"), e -> e.toContain("b"));
        assertPasses(List.of("a", "b"), e -> e.not().toContain("c"));
        assertFails(42, e -> e.toContain("4"), "must be a string, collection or array");
        assertPasses(List.of(List.of(1)), e -> e.toContainEqual(List.of(1.0)));
        assertPasses(List.of(1, 2), e -> e.toHaveLength(2));
        assertPasses("", e -> e.toHaveLength(0));
        assertFails(5, e -> e.toHaveLength(1), "does not have a length");
    }

    @Test
    void testNumbers() {
        assertPasses(3, e -> e.toBeGreaterThan(2));
        assertFails(2, e -> e.toBeGreaterThan(2), "Expected: 2");
        assertPasses(2, e -> e.toBeGreaterThanOrEqual(2));
        assertPasses(1.5, e -> e.toBeLessThan(2));
        assertPasses(2, e -> e.toBeLessThanOrEqual(2.0));
        assertPasses(0.1 + 0.2, e -> e.toBeCloseTo(0.3));
        assertPasses(0.1 + 0.2, e -> e.toBeCloseTo(0.3, 5));
        assertFails("1", e -> e.toBeGreaterThan(0), "must be a number");
    }

    @Test
    void testNullAndTruthiness() {
        assertPasses(null, ValueExpectation::toBeNull);
        assertPasses(null, ValueExpectation::toBeUndefined);
        assertPasses("x", e -> e.not().toBeNull());
        assertFails("x", ValueExpectation::toBeNull, "expect(received).toBeNull()\n\nReceived: \"x\"");
        assertPasses(0, ValueExpectation::toBeDefined);
        assertFails(null, ValueExpectation::toBeDefined, "Received: null");
        assertPasses(1, ValueExpectation::toBeTruthy);
        assertPasses("", ValueExpectation::toBeFalsy);
        assertFails("", ValueExpectation::toBeTruthy, "toBeTruthy()");
    }

    @Test
    void testMatch() {
        assertPasses("x arrow-down y", e -> e.toMatch("arrow-down"));
        assertPasses("strength-meter-3", e -> e.toMatch(TextMatch.regex("strength-meter-[1-5]")));
        assertPasses("2024-01-02T03:04:05.678Z", e -> e.toMatch(TextMatch.regex(
                "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(?:Z|[+-]\\d{2}:?\\d{2})$")));
        assertFails("abc", e -> e.toMatch("x"), "Expected: /x/");
        assertFails(null, e -> e.toMatch("x"), "must be a string");
    }

    @Test
    void testMatchObjectAndSatisfy() {
        assertPasses(Map.of("type", "Annotation", "uuid", "77"), e -> e.toMatchObject(Map.of("uuid", "77")));
        assertFails(Map.of("uuid", "77"), e -> e.toMatchObject(Map.of("uuid", "78")),
                "Expected: { uuid: \"78\" }");
        assertPasses(4, e -> e.toSatisfy("isEven", value -> ((Integer) value) % 2 == 0));
        assertFails(3, e -> e.toSatisfy("isEven", value -> ((Integer) value) % 2 == 0), "Expected: isEven");
    }

    @Test
    void testValueIsReadWhenTheStepRuns() {
        final List<String> saved = new ArrayList<>();
        final Play play = new Play();
        play.expect(() -> saved.size()).toBe(1);
        saved.add("x");

        play.getSteps().get(0).run(null);
    }

    @Test
    void testDescriptions() {
        final Play play = new Play();
        play.expect(() -> 1).toBe(1);
        play.expect("saved.size()", () -> 1).not().toBeGreaterThan(2);
        play.expect(Value.of("rows", () -> List.of())).toHaveLength(0);
        play.expect(() -> "a").toMatch(TextMatch.regex("^a$", "i"));
        play.expect(() -> null).toBeNull();
        play.expect(() -> 0.3).toBeCloseTo(0.3, 3);

        // Without a root (the step hasn't run), the value isn't read, so its label is shown
        assertThat(play.getSteps()).extracting(step -> step.describe(null)).containsExactly(
                "expect(value).toBe(1)",
                "expect(saved.size()).not.toBeGreaterThan(2)",
                "expect(rows).toHaveLength(0)",
                "expect(value).toMatch(/^a$/i)",
                "expect(value).toBeNull()",
                "expect(value).toBeCloseTo(0.3, 3)");
    }

    @Test
    void testFailureMessage() {
        assertThat(ValueExpectation.failureMessage("toBeNull", null, false, 1))
                .isEqualTo("expect(received).toBeNull()\n\nReceived: 1");
        assertThat(ValueExpectation.failureMessage("toEqual", "[1]", true, List.of(1)))
                .isEqualTo("expect(received).not.toEqual(expected)\n\nExpected: not [1]\nReceived: [1]");
    }

    private static void assertPasses(final Object actual, final Consumer<ValueExpectation> matcher) {
        final Play play = new Play();
        matcher.accept(play.expect(() -> actual));
        assertThat(play.getSteps()).hasSize(1);
        play.getSteps().get(0).run(null);
    }

    private static void assertFails(final Object actual,
                                    final Consumer<ValueExpectation> matcher,
                                    final String messagePart) {
        final Play play = new Play();
        matcher.accept(play.expect(() -> actual));
        assertThatThrownBy(() -> play.getSteps().get(0).run(null))
                .isInstanceOf(PlayException.class)
                .hasMessageContaining(messagePart);
    }
}
