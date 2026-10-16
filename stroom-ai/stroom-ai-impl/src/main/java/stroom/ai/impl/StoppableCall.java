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

package stroom.ai.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/// Runs a call that can block for a long time, e.g. an MCP tool call (whose client has no way to cancel it), so
/// that the run making it can give up on it as soon as the user stops the run, rather than when the call returns.
/// The call runs on a virtual thread of its own, which is interrupted when it is given up on; whatever the call
/// started elsewhere (e.g. on the MCP server) may still finish.
final class StoppableCall {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoppableCall.class);

    private StoppableCall() {
        // Static only.
    }

    /// @param call       The call. It must not need anything held by the calling thread (e.g. its security
    ///                   context), as it runs on another.
    /// @param stop       Set when the call is to be given up on.
    /// @param checkEvery How often to look at `stop` while the call runs.
    /// @return What the call returned.
    /// @throws StoppedException If `stop` was set before the call returned.
    /// @throws RuntimeException What the call threw, as it threw it if it was unchecked.
    static <T> T run(final Supplier<T> call, final AtomicBoolean stop, final Duration checkEvery) {
        if (stop.get()) {
            throw new StoppedException();
        }
        final CompletableFuture<T> future = new CompletableFuture<>();
        final Thread thread = Thread.ofVirtual().name("stoppable-call").start(() -> {
            try {
                future.complete(call.get());
            } catch (final Throwable e) {
                future.completeExceptionally(e);
            }
        });
        try {
            while (true) {
                try {
                    return future.get(checkEvery.toMillis(), TimeUnit.MILLISECONDS);
                } catch (final TimeoutException e) {
                    if (stop.get()) {
                        LOGGER.debug("Giving up on a call as a stop was requested");
                        thread.interrupt();
                        throw new StoppedException();
                    }
                }
            }
        } catch (final InterruptedException e) {
            thread.interrupt();
            Thread.currentThread().interrupt();
            throw new StoppedException();
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof final RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (e.getCause() instanceof final Error error) {
                throw error;
            }
            throw new RuntimeException(e.getCause());
        }
    }


    // --------------------------------------------------------------------------------


    /// Thrown when a call is given up on because a stop was requested.
    static final class StoppedException extends RuntimeException {

        StoppedException() {
            super("Stopped by the user before it finished");
        }
    }
}
