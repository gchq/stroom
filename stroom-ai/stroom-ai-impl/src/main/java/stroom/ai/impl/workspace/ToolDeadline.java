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

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/// A time limit on a tool call, for work that could otherwise run for a very long time (e.g. a search with a
/// pattern that backtracks badly, or a large table step). The limit is held per thread; long-running code calls
/// {@link #check()} as it goes, and pattern matching checks it through [DeadlineCharSequence].
///
/// Time spent waiting for the user (see [#paused(Supplier)]) doesn't count against a limit: the limits are kept in
/// the thread's working time, the time since its first limit was set less the time it has spent paused.
public final class ToolDeadline {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();
    // How long the thread has spent paused, in nanoseconds, while it has had a limit.
    private static final ThreadLocal<Long> PAUSED_NANOS = new ThreadLocal<>();

    private ToolDeadline() {
        // Static only.
    }

    /// Does some work with a time limit. A limit already in force (e.g. for a pipeline of tool steps) is kept if
    /// it is sooner.
    ///
    /// @param limit The time limit.
    /// @param work  The work.
    /// @param <T>   The type of the result.
    /// @return The result.
    /// @throws ToolTimeoutException if the work runs past the limit.
    public static <T> T run(final Duration limit, final Supplier<T> work) {
        final Long previous = DEADLINE_NANOS.get();
        if (previous == null) {
            PAUSED_NANOS.set(0L);
        }
        final long deadline = workingNanos() + limit.toNanos();
        DEADLINE_NANOS.set(previous != null && previous - deadline < 0
                ? previous
                : deadline);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                DEADLINE_NANOS.remove();
                PAUSED_NANOS.remove();
            } else {
                DEADLINE_NANOS.set(previous);
            }
        }
    }

    /// Does some work that waits for the user (e.g. a form they fill in), so that the time it takes doesn't count
    /// against the time limits in force: they are pushed back by as long as it took.
    ///
    /// @param work The work.
    /// @param <T>  The type of the result.
    /// @return The result.
    public static <T> T paused(final Supplier<T> work) {
        final long start = System.nanoTime();
        try {
            return work.get();
        } finally {
            final Long paused = PAUSED_NANOS.get();
            if (paused != null) {
                PAUSED_NANOS.set(paused + (System.nanoTime() - start));
            }
        }
    }

    /// @throws ToolTimeoutException if the current work has run past its time limit, or the thread has been
    /// interrupted (e.g. the run has been stopped).
    public static void check() {
        final Long deadline = DEADLINE_NANOS.get();
        if (deadline != null && workingNanos() - deadline > 0) {
            throw new ToolTimeoutException();
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new ToolTimeoutException();
        }
    }

    /// @return How long the current work has left before its time limit (zero once past it), or empty if it has
    /// no time limit.
    public static Optional<Duration> remaining() {
        final Long deadline = DEADLINE_NANOS.get();
        if (deadline == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofNanos(Math.max(0L, deadline - workingNanos())));
    }

    // The time now, less the time the thread has spent paused.
    private static long workingNanos() {
        final Long paused = PAUSED_NANOS.get();
        return System.nanoTime() - (paused == null
                ? 0L
                : paused);
    }
}
