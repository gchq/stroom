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

package stroom.ai.impl.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSafePattern {

    @Test
    void testLiteralByDefault() {
        final SafePattern pattern = SafePattern.of("a.b", false, false);

        assertThat(pattern.find("xa.by")).isTrue();
        // Not a pattern: '.' only matches '.'.
        assertThat(pattern.find("axb")).isFalse();
        assertThat(pattern.find("A.B")).isFalse();
        assertThat(SafePattern.of("a.b", false, true).find("XA.BY")).isTrue();
        assertThat(SafePattern.of("A.b", false, true).find("xa.by")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"System|Environment", "error.*host", "a.+b", "\\d+ events", "host[12]", "^ERROR",
            "done$", "\\bword"})
    void testALiteralThatLooksLikeARegexIsSaidToBeOne(final String text) {
        assertThat(SafePattern.of(text, false, false).isLiteralThatLooksLikeRegex()).isTrue();
        assertThat(SafePattern.of(text, false, true).isLiteralThatLooksLikeRegex()).isTrue();
        // Only a literal search may have been meant otherwise.
        assertThat(SafePattern.of(text, true, false).isLiteralThatLooksLikeRegex()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"event 30", "host=fw0", "a.b", "1.2.3.4", "C:\\Temp", "[]", "cost $5", "x^2"})
    void testOrdinaryTextIsNotSaidToBeARegex(final String text) {
        assertThat(SafePattern.of(text, false, false).isLiteralThatLooksLikeRegex()).isFalse();
    }

    @Test
    void testRegex() {
        assertThat(SafePattern.of("ERR(OR)?\\s+\\d+", true, false).find("ERROR 42")).isTrue();
        assertThat(SafePattern.of("error", true, true).find("ERROR")).isTrue();
        assertThatThrownBy(() -> SafePattern.of("(", true, false)).hasMessageContaining("not a valid");
        assertThatThrownBy(() -> SafePattern.of("a".repeat(201), true, false)).hasMessageContaining("at most");
        assertThatThrownBy(() -> SafePattern.of("", false, false)).isInstanceOf(WorkspaceException.class);
    }

    @Test
    void testCatastrophicBacktrackingIsStopped() {
        // Patterns that backtrack catastrophically even with the JDK's memoisation of simple loops.
        assertStopped(SafePattern.of("(.*?,){25}x", true, false), "a,".repeat(40));
        assertStopped(SafePattern.of("^(a+)+\\1b$", true, false), "a".repeat(30));
    }

    private static void assertStopped(final SafePattern evil, final String input) {
        final long start = System.nanoTime();

        assertThatThrownBy(() -> ToolDeadline.run(Duration.ofMillis(200), () -> evil.find(input)))
                .isInstanceOf(ToolTimeoutException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void testAModelsRegexIsLimitedInLength() {
        assertThat(SafePattern.compile("a".repeat(SafePattern.MAX_REGEX_LENGTH), 0).pattern())
                .hasSize(SafePattern.MAX_REGEX_LENGTH);
        // Said as a PatternSyntaxException, so a caller that says why a regex is invalid says so.
        assertThatThrownBy(() -> SafePattern.compile("a".repeat(SafePattern.MAX_REGEX_LENGTH + 1), 0))
                .isInstanceOf(PatternSyntaxException.class)
                .hasMessageContaining("may be at most " + SafePattern.MAX_REGEX_LENGTH);
    }

    @Test
    void testAModelsRegexIsStoppedWhereTheCallHasNoTimeLimit() {
        // Before, a splitter's or extraction's regex ran with no time limit in a tool that sets none.
        final long start = System.nanoTime();
        final Pattern evil = SafePattern.compile("(.*?,){25}x", 0);

        assertThatThrownBy(() -> SafePattern.limited("(.*?,){25}x",
                () -> SafePattern.matcher(evil, "a,".repeat(40)).find()))
                .isInstanceOf(PatternTimeoutException.class)
                .hasMessageStartingWith("The pattern '(.*?,){25}x' took too long to match (more than "
                                        + SafePattern.REGEX_TIME_LIMIT.toSeconds() + " s) and was stopped");
        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isLessThan(SafePattern.REGEX_TIME_LIMIT.plusSeconds(10));
    }

    @Test
    void testTheCallsOwnSoonerTimeLimitIsTheCalls() {
        assertThatThrownBy(() -> ToolDeadline.run(Duration.ofMillis(100), () -> SafePattern.limited("x", () -> {
            busyWait(Duration.ofMillis(200));
            ToolDeadline.check();
            return null;
        }))).isExactlyInstanceOf(ToolTimeoutException.class);
        assertThat(SafePattern.limited("x", () -> "done")).isEqualTo("done");
    }

    @Test
    void testDeadlines() {
        // A nested limit cannot extend an outer one.
        assertThatThrownBy(() -> ToolDeadline.run(Duration.ofMillis(50), () ->
                ToolDeadline.run(Duration.ofHours(1), () -> {
                    busyWait(Duration.ofMillis(100));
                    ToolDeadline.check();
                    return null;
                }))).isInstanceOf(ToolTimeoutException.class);
        // No limit outside a tool call.
        ToolDeadline.check();
        assertThat(ToolDeadline.run(Duration.ofSeconds(5), () -> "ok")).isEqualTo("ok");
    }

    @Test
    void testInterruptStopsWork() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(ToolDeadline::check).isInstanceOf(ToolTimeoutException.class);
        } finally {
            // Clear the flag for other tests.
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    private static void busyWait(final Duration duration) {
        final long end = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }
}
