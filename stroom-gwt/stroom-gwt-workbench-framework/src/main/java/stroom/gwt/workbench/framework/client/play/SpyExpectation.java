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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/// An expectation about the calls to a [Spy], the equivalent of
/// `expect(spy).toHaveBeenCalled()` etc. in React Storybook's play functions. Each assertion
/// method adds a step to the play function, which checks the calls made by the time it runs.
public final class SpyExpectation {

    private final Play play;
    private final Spy spy;
    private final boolean negated;

    /// @param play    The play function to add the expectation's step to.
    /// @param spy     The spy.
    /// @param negated True if the expectation is negated with `.not`.
    SpyExpectation(final Play play, final Spy spy, final boolean negated) {
        this.play = play;
        this.spy = Objects.requireNonNull(spy, "spy");
        this.negated = negated;
    }

    /// @return The opposite expectation, e.g. `expect(spy).not.toHaveBeenCalled()`.
    public SpyExpectation not() {
        return new SpyExpectation(play, spy, !negated);
    }

    /// Expects the spy to have been called at least once.
    public void toHaveBeenCalled() {
        add("toHaveBeenCalled()", calls -> !calls.isEmpty(), "Expected number of calls: " + (negated
                ? "0"
                : ">= 1"));
    }

    /// Expects the spy to have been called a number of times.
    ///
    /// @param times The number of calls.
    public void toHaveBeenCalledTimes(final int times) {
        add("toHaveBeenCalledTimes(" + times + ")", calls -> calls.size() == times,
                "Expected number of calls: " + (negated
                        ? "not "
                        : "") + times);
    }

    /// Expects at least one call to have had the arguments. Pass a single null as `(Object) null`.
    ///
    /// @param args The arguments, which may be [ValueMatcher]s, e.g.
    ///             `ValueMatcher.objectContaining(Map.of("id", 1))`.
    public void toHaveBeenCalledWith(final Object... args) {
        final List<Object> expected = toList(args);
        add("toHaveBeenCalledWith(" + Values.formatAll(expected) + ")", calls -> {
            for (final List<Object> call : calls) {
                if (Values.argumentsMatch(call, expected)) {
                    return true;
                }
            }
            return false;
        }, "Expected: " + (negated
                ? "not "
                : "") + Values.formatAll(expected));
    }

    /// Expects the last call to have had the arguments. Pass a single null as `(Object) null`.
    ///
    /// @param args The arguments, which may be [ValueMatcher]s.
    public void toHaveBeenLastCalledWith(final Object... args) {
        final List<Object> expected = toList(args);
        add("toHaveBeenLastCalledWith(" + Values.formatAll(expected) + ")",
                calls -> !calls.isEmpty() && Values.argumentsMatch(calls.get(calls.size() - 1), expected),
                "Expected: " + (negated
                        ? "not "
                        : "") + Values.formatAll(expected));
    }

    /// Expects a call to have had the arguments. Pass a single null as `(Object) null`.
    ///
    /// @param nth  Which call, counting from 1 as Jest does.
    /// @param args The arguments, which may be [ValueMatcher]s.
    public void toHaveBeenNthCalledWith(final int nth, final Object... args) {
        if (nth < 1) {
            throw new IllegalArgumentException("nth must be 1 or more: " + nth);
        }
        final List<Object> expected = toList(args);
        add("toHaveBeenNthCalledWith(" + nth + (expected.isEmpty()
                        ? ""
                        : ", " + Values.formatAll(expected)) + ")",
                calls -> calls.size() >= nth && Values.argumentsMatch(calls.get(nth - 1), expected),
                "Expected call " + nth + ": " + (negated
                        ? "not "
                        : "") + Values.formatAll(expected));
    }

    private static List<Object> toList(final Object[] args) {
        return args != null
                ? Arrays.asList(args)
                : Collections.singletonList(null);
    }

    private void add(final String matcher, final Predicate<List<List<Object>>> test, final String expectedLine) {
        final String description = "expect(" + spy.getName() + ")" + (negated
                ? ".not"
                : "") + "." + matcher;
        play.addStep(PlayStep.action(root -> description, root -> {
            final List<List<Object>> calls = spy.getCalls();
            if (test.test(calls) == negated) {
                throw new PlayException(failureMessage(description, expectedLine, calls));
            }
        }));
    }

    /// @param description  The expectation, e.g. `expect(onClick).toHaveBeenCalled()`.
    /// @param expectedLine What was expected, e.g. `Expected number of calls: >= 1`.
    /// @param calls        The spy's calls.
    /// @return A message like Jest's, listing the calls.
    static String failureMessage(final String description,
                                 final String expectedLine,
                                 final List<List<Object>> calls) {
        final StringBuilder sb = new StringBuilder(description).append(" failed\n\n")
                .append(expectedLine).append('\n')
                .append("Received number of calls: ").append(calls.size());
        for (int i = 0; i < calls.size(); i++) {
            sb.append('\n').append(i + 1).append(": ").append(Values.formatAll(calls.get(i)));
        }
        return sb.toString();
    }
}
