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

import com.google.gwt.dom.client.Element;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// An expectation about a value, the equivalent of `expect(value).toBe(...)` etc. in React
/// Storybook's play functions. The value is read when the step runs (and each time a
/// `waitFor(...)` retries it), e.g.
/// ```
/// play.expect(() -> saved.size()).toBe(1);
/// play.expect(play.getByRole("button").textContent()).toBe("Save");
/// play.waitFor(() -> play.expect(play.getAllByRole("row").count()).toBeGreaterThan(2));
/// ```
/// Each assertion method adds a step to the play function. Numbers compare by value whatever
/// their Java type, and null stands for both `null` and `undefined`.
public final class ValueExpectation {

    private final Play play;
    private final String label;
    private final Supplier<?> supplier;
    private final boolean negated;

    /// @param play     The play function to add the expectation's step to.
    /// @param label    How the value is shown if it can't be read.
    /// @param supplier Reads the value.
    /// @param negated  True if the expectation is negated with `.not`.
    ValueExpectation(final Play play, final String label, final Supplier<?> supplier, final boolean negated) {
        this.play = play;
        this.label = Objects.requireNonNull(label, "label");
        this.supplier = Objects.requireNonNull(supplier, "supplier");
        this.negated = negated;
    }

    /// @return The opposite expectation, e.g. `expect(value).not.toBe(1)`.
    public ValueExpectation not() {
        return new ValueExpectation(play, label, supplier, !negated);
    }

    /// Expects the value to be the same as another, the equivalent of `toBe`.
    ///
    /// @param expected The expected value, or a [ValueMatcher].
    public void toBe(final Object expected) {
        add("toBe", Values.format(expected), actual -> Values.isSame(actual, expected));
    }

    /// Expects the value to equal another, comparing maps, collections and arrays item by item,
    /// the equivalent of `toEqual`.
    ///
    /// @param expected The expected value, which may be or contain [ValueMatcher]s.
    public void toEqual(final Object expected) {
        add("toEqual", Values.format(expected), actual -> Values.deepEquals(actual, expected));
    }

    /// Expects a string to contain some text, or a collection or array to contain an item, the
    /// equivalent of `toContain`.
    ///
    /// @param item The text or item.
    public void toContain(final Object item) {
        add("toContain", Values.format(item), actual -> {
            requireContainer(actual);
            return Values.containsItem(actual, item, false);
        });
    }

    /// Expects a collection or array to contain an item equal to another, the equivalent of
    /// `toContainEqual`.
    ///
    /// @param item The item, which may be or contain [ValueMatcher]s.
    public void toContainEqual(final Object item) {
        add("toContainEqual", Values.format(item), actual -> {
            requireContainer(actual);
            return Values.containsItem(actual, item, true);
        });
    }

    /// Expects a string, collection, map or array to have a length, the equivalent of
    /// `toHaveLength`.
    ///
    /// @param length The length.
    public void toHaveLength(final int length) {
        add("toHaveLength", String.valueOf(length), actual -> {
            final Integer actualLength = Values.lengthOf(actual);
            if (actualLength == null) {
                throw new PlayException("expect(received).toHaveLength(expected)\n\n"
                                        + "Received value does not have a length: " + Values.format(actual));
            }
            return actualLength == length;
        });
    }

    /// Expects a number to be greater than another, the equivalent of `toBeGreaterThan`.
    ///
    /// @param number The other number.
    public void toBeGreaterThan(final Number number) {
        add("toBeGreaterThan", Values.format(number), actual -> Values.compare(actual, number) > 0);
    }

    /// Expects a number to be greater than or equal to another, the equivalent of
    /// `toBeGreaterThanOrEqual`.
    ///
    /// @param number The other number.
    public void toBeGreaterThanOrEqual(final Number number) {
        add("toBeGreaterThanOrEqual", Values.format(number), actual -> Values.compare(actual, number) >= 0);
    }

    /// Expects a number to be less than another, the equivalent of `toBeLessThan`.
    ///
    /// @param number The other number.
    public void toBeLessThan(final Number number) {
        add("toBeLessThan", Values.format(number), actual -> Values.compare(actual, number) < 0);
    }

    /// Expects a number to be less than or equal to another, the equivalent of
    /// `toBeLessThanOrEqual`.
    ///
    /// @param number The other number.
    public void toBeLessThanOrEqual(final Number number) {
        add("toBeLessThanOrEqual", Values.format(number), actual -> Values.compare(actual, number) <= 0);
    }

    /// Expects a number to be within 0.005 of another, the equivalent of `toBeCloseTo(number)`.
    ///
    /// @param number The other number.
    public void toBeCloseTo(final Number number) {
        toBeCloseTo(number, 2);
    }

    /// Expects a number to be close to another, the equivalent of `toBeCloseTo(number, numDigits)`.
    ///
    /// @param number    The other number.
    /// @param numDigits The number of decimal places to check.
    public void toBeCloseTo(final Number number, final int numDigits) {
        add("toBeCloseTo", Values.format(number) + ", " + numDigits,
                actual -> Values.isCloseTo(actual, number, numDigits));
    }

    /// Expects the value to be null, the equivalent of `toBeNull`.
    public void toBeNull() {
        add("toBeNull", null, actual -> actual == null);
    }

    /// Expects the value to be null, the equivalent of `toBeUndefined` (Java has no `undefined`).
    public void toBeUndefined() {
        add("toBeUndefined", null, actual -> actual == null);
    }

    /// Expects the value not to be null, the equivalent of `toBeDefined`.
    public void toBeDefined() {
        add("toBeDefined", null, actual -> actual != null);
    }

    /// Expects the value to be truthy in JavaScript's terms, i.e. not null, false, zero, NaN or an
    /// empty string, the equivalent of `toBeTruthy`.
    public void toBeTruthy() {
        add("toBeTruthy", null, Values::isTruthy);
    }

    /// Expects the value to be falsy in JavaScript's terms, i.e. null, false, zero, NaN or an
    /// empty string, the equivalent of `toBeFalsy`.
    public void toBeFalsy() {
        add("toBeFalsy", null, actual -> !Values.isTruthy(actual));
    }

    /// Expects a string to contain some text, the equivalent of `toMatch("text")`.
    ///
    /// @param text The text.
    public void toMatch(final String text) {
        toMatch(TextMatch.containing(text));
    }

    /// Expects a string to match, the equivalent of `toMatch(/regex/)`.
    ///
    /// @param match How to match, e.g. `TextMatch.regex("strength-meter-[1-5]")`.
    public void toMatch(final TextMatch match) {
        add("toMatch", match.describe(), actual -> {
            if (!(actual instanceof String)) {
                throw new PlayException("expect(received).toMatch(expected)\n\n"
                                        + "Received value must be a string: " + Values.format(actual));
            }
            return match.matches((String) actual);
        });
    }

    /// Expects a map to have (at least) some entries, the equivalent of `toMatchObject({...})`.
    ///
    /// @param expected The entries; values may be [ValueMatcher]s or nested maps, which only need
    ///                 to match part of the actual value too.
    public void toMatchObject(final Map<String, ?> expected) {
        add("toMatchObject", Values.format(expected), actual -> Values.matchesObject(actual, expected));
    }

    /// Expects the value to satisfy a condition, for checks with no Jest equivalent in Java.
    ///
    /// @param description How the condition is shown, e.g. `isSorted`.
    /// @param condition   Tests the value, which may be null.
    public void toSatisfy(final String description, final Predicate<Object> condition) {
        add("toSatisfy", description, condition::test);
    }

    private static void requireContainer(final Object actual) {
        if (!(actual instanceof String) && !Values.isCollectionLike(actual)) {
            throw new PlayException("expect(received).toContain(expected)\n\n"
                                    + "Received value must be a string, collection or array: "
                                    + Values.format(actual));
        }
    }

    private void add(final String matcher, final String expectedText, final Predicate<Object> test) {
        final String call = matcher + "(" + (expectedText != null
                ? expectedText
                : "") + ")";
        final String not = negated
                ? ".not"
                : "";
        final Function<Element, String> describer = root -> "expect("
                                                           + (root != null
                ? Values.format(supplier.get())
                : label)
                                                           + ")" + not + "." + call;
        play.addStep(PlayStep.action(describer, root -> {
            final Object actual = supplier.get();
            if (test.test(actual) == negated) {
                throw new PlayException(failureMessage(matcher, expectedText, negated, actual));
            }
        }));
    }

    /// @param matcher      The matcher, e.g. `toBe`.
    /// @param expectedText The expected value formatted, or null if the matcher has no argument.
    /// @param negated      True if the expectation is negated.
    /// @param actual       The actual value.
    /// @return A message like Jest's, e.g. `expect(received).toBe(expected)`, then the expected
    /// and received values on separate lines.
    static String failureMessage(final String matcher,
                                 final String expectedText,
                                 final boolean negated,
                                 final Object actual) {
        final String not = negated
                ? "not "
                : "";
        final StringBuilder sb = new StringBuilder("expect(received).")
                .append(negated
                        ? "not."
                        : "")
                .append(matcher)
                .append(expectedText != null
                        ? "(expected)"
                        : "()")
                .append("\n\n");
        if (expectedText != null) {
            sb.append("Expected: ").append(not).append(expectedText).append('\n');
        }
        return sb.append("Received: ").append(Values.format(actual)).toString();
    }
}
