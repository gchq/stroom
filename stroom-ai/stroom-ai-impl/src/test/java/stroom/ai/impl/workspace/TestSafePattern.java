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

import java.time.Duration;

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
