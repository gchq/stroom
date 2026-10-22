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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// The requests of an MCP session being worked on, by the id of the client's request, so that a request can be
/// cancelled: by the client (`notifications/cancelled`), when its HTTP request ends, or when the session ends. A
/// request is worked on by the thread that serves it, which is interrupted to cancel it; tools that check (see
/// [stroom.ai.impl.workspace.ToolDeadline#check()]) then stop.
final class RunningCalls {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RunningCalls.class);

    private final Map<String, RunningCall> byRequestId = new ConcurrentHashMap<>();
    private final Set<RunningCall> all = ConcurrentHashMap.newKeySet();

    /// Counts the current thread as working on a request, until the call it gives is closed.
    ///
    /// @param requestId The id of the client's request, or null if it isn't known (then only the end of the
    ///                  session or of its HTTP request cancels it).
    /// @return The running call, to close (on the same thread) when the work is done.
    RunningCall start(final Object requestId) {
        final RunningCall call = new RunningCall(this, key(requestId), Thread.currentThread());
        all.add(call);
        if (call.key != null) {
            byRequestId.put(call.key, call);
        }
        return call;
    }

    /// Cancels the work on a request, if it is still being worked on.
    ///
    /// @param requestId The id of the client's request.
    /// @return True if it was being worked on.
    boolean cancel(final Object requestId) {
        final String key = key(requestId);
        final RunningCall call = key == null
                ? null
                : byRequestId.get(key);
        if (call == null) {
            return false;
        }
        call.cancel();
        return true;
    }

    /// Cancels the work on every request, e.g. as the session has ended.
    void cancelAll() {
        List.copyOf(all).forEach(RunningCall::cancel);
    }

    /// @return How many requests are being worked on.
    int count() {
        return all.size();
    }

    private void ended(final RunningCall call) {
        all.remove(call);
        if (call.key != null) {
            byRequestId.remove(call.key, call);
        }
    }

    // JSON-RPC ids are strings or numbers, which may be read as Integer or Long: 1 and 1L are the same request.
    private static String key(final Object requestId) {
        return requestId == null
                ? null
                : String.valueOf(requestId);
    }


    // --------------------------------------------------------------------------------


    /// The work on one request, by one thread. Once closed it can no longer be cancelled, so an interrupt can't
    /// reach the thread after the work is done (it is the server's, and goes on to other requests).
    static final class RunningCall implements AutoCloseable {

        private final RunningCalls calls;
        private final String key;
        private final Thread thread;
        private boolean done;
        private boolean cancelled;

        private RunningCall(final RunningCalls calls, final String key, final Thread thread) {
            this.calls = calls;
            this.key = key;
            this.thread = thread;
        }

        /// Interrupts the thread working on the request, unless the work is done.
        synchronized void cancel() {
            if (!done && !cancelled) {
                cancelled = true;
                LOGGER.debug(() -> "Cancelling MCP request " + key);
                thread.interrupt();
            }
        }

        /// @return True if the request was cancelled while it was worked on.
        synchronized boolean isCancelled() {
            return cancelled;
        }

        /// Ends the work, clearing any interrupt the cancelling left on the thread.
        @Override
        public synchronized void close() {
            done = true;
            calls.ended(this);
            if (Thread.currentThread() == thread) {
                Thread.interrupted();
            }
        }
    }
}
