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
import java.util.function.Supplier;

/// A time limit on a tool call, for work that could otherwise run for a very long time (e.g. a search with a
/// pattern that backtracks badly, or a large table step). The limit is held per thread; long-running code calls
/// {@link #check()} as it goes, and pattern matching checks it through [DeadlineCharSequence].
public final class ToolDeadline {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

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
        final long deadline = System.nanoTime() + limit.toNanos();
        DEADLINE_NANOS.set(previous != null && previous - deadline < 0
                ? previous
                : deadline);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                DEADLINE_NANOS.remove();
            } else {
                DEADLINE_NANOS.set(previous);
            }
        }
    }

    /// @throws ToolTimeoutException if the current work has run past its time limit, or the thread has been
    /// interrupted (e.g. the run has been stopped).
    public static void check() {
        final Long deadline = DEADLINE_NANOS.get();
        if (deadline != null && System.nanoTime() - deadline > 0) {
            throw new ToolTimeoutException();
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new ToolTimeoutException();
        }
    }
}
