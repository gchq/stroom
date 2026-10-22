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

package stroom.mcp.impl;

import stroom.ai.impl.AiChatRunManager.RunSlot;
import stroom.ai.impl.workspace.ToolDeadline;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/// The run slot a heavy MCP tool call holds (see [stroom.ai.impl.tools.StroomTool#usesRunSlot()]), given back while
/// the call waits for the user (a form can wait up to the form timeout), so a waiting call doesn't keep the user's
/// other work from starting, and taken again once they have answered.
final class McpCallSlot implements AutoCloseable {

    /// How often a call that has had its answer looks for a free slot again.
    static final Duration RETAKE_INTERVAL = Duration.ofMillis(250);

    private final Supplier<Optional<RunSlot>> taker;
    private RunSlot held;

    private McpCallSlot(final Supplier<Optional<RunSlot>> taker) {
        this.taker = taker;
    }

    /// @return A call that takes no slot.
    static McpCallSlot none() {
        return new McpCallSlot(null);
    }

    /// @param taker Takes one of the user's slots, if one is free.
    /// @return The slot, taken; or empty if none is free.
    static Optional<McpCallSlot> take(final Supplier<Optional<RunSlot>> taker) {
        Objects.requireNonNull(taker, "taker");
        final McpCallSlot slot = new McpCallSlot(taker);
        return slot.tryTake()
                ? Optional.of(slot)
                : Optional.empty();
    }

    /// Does some work that waits for the user without the slot, then takes a slot again, waiting for one to come
    /// free if need be (within the call's time limit, see [ToolDeadline]).
    ///
    /// @param work The work, e.g. showing a form.
    /// @param <T>  The type of its result.
    /// @return Its result.
    /// @throws CancellationException if the call is cancelled while it waits for a slot.
    /// @throws stroom.ai.impl.workspace.ToolTimeoutException if the call's time runs out while it waits for one.
    <T> T releasedWhile(final Supplier<T> work) {
        if (taker == null) {
            return work.get();
        }
        release();
        // If the work fails, the call ends without the slot.
        final T result = work.get();
        retake();
        return result;
    }

    /// Gives the slot back, if it is held.
    @Override
    public void close() {
        release();
    }

    private boolean tryTake() {
        final Optional<RunSlot> slot = taker.get();
        held = slot.orElse(null);
        return held != null;
    }

    private void release() {
        if (held != null) {
            held.close();
            held = null;
        }
    }

    private void retake() {
        while (!tryTake()) {
            ToolDeadline.check();
            try {
                Thread.sleep(RETAKE_INTERVAL.toMillis());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("The call was cancelled while it waited for a free run slot.");
            }
        }
    }
}
