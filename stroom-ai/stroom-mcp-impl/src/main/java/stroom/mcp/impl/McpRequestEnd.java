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

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.ArrayList;
import java.util.List;

/// Tells the work on an MCP request when the client's HTTP request has ended: the transport answers a request as a
/// stream (an async request), which ends when the answer has been sent, the client goes away, or sending to it
/// fails. Work still going on then is for no one, so it is cancelled (see [RunningCalls]).
///
/// [McpServlet] gives each request one, as a request attribute, and wraps the request so that the transport's
/// `startAsync()` adds its listener.
final class McpRequestEnd implements AsyncListener {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpRequestEnd.class);

    /// The request attribute holding it.
    static final String ATTRIBUTE = McpRequestEnd.class.getName();

    private final List<Runnable> listeners = new ArrayList<>();
    private boolean ended;

    /// @param request A request to `/mcp`.
    /// @return The request, wrapped so the end of its async answer is seen, with its [McpRequestEnd] set.
    static HttpServletRequest watch(final HttpServletRequest request) {
        final McpRequestEnd end = new McpRequestEnd();
        request.setAttribute(ATTRIBUTE, end);
        return new HttpServletRequestWrapper(request) {
            @Override
            public AsyncContext startAsync() {
                final AsyncContext context = super.startAsync();
                context.addListener(end);
                return context;
            }
        };
    }

    /// @param request A request.
    /// @return Its [McpRequestEnd], or null if it has none.
    static McpRequestEnd of(final HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof final McpRequestEnd end
                ? end
                : null;
    }

    /// @param listener Told once, when the request ends; at once if it has ended already.
    void onEnd(final Runnable listener) {
        final boolean alreadyEnded;
        synchronized (this) {
            alreadyEnded = ended;
            if (!alreadyEnded) {
                listeners.add(listener);
            }
        }
        if (alreadyEnded) {
            listener.run();
        }
    }

    /// The answer has been sent, or the transport gave up sending it (e.g. the client went away).
    ///
    /// @param event The event.
    @Override
    public void onComplete(final AsyncEvent event) {
        end("complete");
    }

    /// The request timed out (the transport sets no timeout, so this is the container's doing).
    ///
    /// @param event The event.
    @Override
    public void onTimeout(final AsyncEvent event) {
        end("timed out");
    }

    /// The request failed, e.g. its connection was lost.
    ///
    /// @param event The event.
    @Override
    public void onError(final AsyncEvent event) {
        end("failed: " + (event == null
                ? "unknown"
                : event.getThrowable()));
    }

    /// Nothing is done when the request is made async again.
    ///
    /// @param event The event.
    @Override
    public void onStartAsync(final AsyncEvent event) {
        // Nothing to do.
    }

    /// Ends the request, telling its listeners. Package-private for testing.
    ///
    /// @param how How it ended, for the log.
    void end(final String how) {
        final List<Runnable> toTell;
        synchronized (this) {
            if (ended) {
                return;
            }
            ended = true;
            toTell = List.copyOf(listeners);
            listeners.clear();
        }
        LOGGER.debug("MCP request {}", how);
        for (final Runnable listener : toTell) {
            try {
                listener.run();
            } catch (final RuntimeException e) {
                LOGGER.debug("A listener for the end of an MCP request failed", e);
            }
        }
    }
}
