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

import stroom.security.api.SecurityContext;
import stroom.security.impl.apikey.ApiKeyService;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.HashAlgorithm;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import com.google.inject.Injector;
import io.modelcontextprotocol.spec.HttpHeaders;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.Filter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/// An MCP client of Stroom's REAL endpoint, `/mcp`, as Jetty would serve its requests: each goes through Stroom's
/// security filter (signed in by the user's API key) to the [McpServlet], and so to the MCP SDK's Streamable HTTP
/// transport and [StroomMcpServer]. Only the HTTP requests and responses are stand-ins.
final class McpServletDriver {

    /// The protocol version the client asks for.
    static final String PROTOCOL_VERSION = "2025-06-18";

    private final Filter securityFilter;
    private final McpServlet servlet;
    private final ApiKeyService apiKeyService;
    private final SecurityContext securityContext;

    /// @param injector Stroom's injector, with the real security filter and MCP servlet.
    McpServletDriver(final Injector injector) {
        try {
            // Stroom's security filter, as Jetty runs it before every servlet (SecurityModule binds it).
            this.securityFilter = (Filter) injector.getInstance(Class.forName("stroom.security.impl.SecurityFilter"));
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        this.servlet = injector.getInstance(McpServlet.class);
        this.apiKeyService = injector.getInstance(ApiKeyService.class);
        this.securityContext = injector.getInstance(SecurityContext.class);
    }

    /// Makes an API key for a user, as they would in Stroom.
    ///
    /// @param owner The user.
    /// @return The key, to sign the client's requests in with.
    String apiKey(final UserRef owner) {
        return securityContext.asProcessingUserResult(() -> apiKeyService.create(new CreateHashedApiKeyRequest(
                owner, null, "MCP sessions test " + owner.getSubjectId() + " " + System.nanoTime(), null, true,
                HashAlgorithm.SHA3_256)).getApiKey());
    }

    /// Starts a session: an `initialize` request.
    ///
    /// @param apiKey       The user's API key.
    /// @param canShowForms True if the client says it can show forms (MCP elicitation).
    /// @return The reply, with the session's id ([Reply#sessionId()]) if it started.
    Reply initialize(final String apiKey, final boolean canShowForms) {
        final Map<String, Object> capabilities = canShowForms
                ? Map.of("elicitation", Map.of())
                : Map.of();
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", capabilities);
        params.put("clientInfo", Map.of("name", "test", "version", "1"));
        return post(apiKey, null, request(1, "initialize", params));
    }

    /// Calls a tool in a session: a `tools/call` request.
    ///
    /// @param apiKey    The user's API key.
    /// @param sessionId The session's id.
    /// @param id        The request's id.
    /// @param tool      The tool's name.
    /// @param arguments Its arguments.
    /// @return The reply: the answer is in its stream ([Reply#messages()]).
    Reply call(final String apiKey,
               final String sessionId,
               final int id,
               final String tool,
               final Map<String, Object> arguments) {
        return post(apiKey, sessionId, request(id, "tools/call", Map.of("name", tool, "arguments", arguments)));
    }

    /// Calls a tool in a session on another thread, as a client whose call is still running (e.g. waiting for the
    /// user to answer a form) has it.
    ///
    /// @param executor  Where the call is made.
    /// @param apiKey    The user's API key.
    /// @param sessionId The session's id.
    /// @param id        The request's id.
    /// @param tool      The tool's name.
    /// @param arguments Its arguments.
    /// @return The reply as it is sent (its stream so far), and the call's end.
    Background callInBackground(final ExecutorService executor,
                                final String apiKey,
                                final String sessionId,
                                final int id,
                                final String tool,
                                final Map<String, Object> arguments) {
        final Reply reply = new Reply();
        final String body = request(id, "tools/call", Map.of("name", tool, "arguments", arguments));
        final Future<?> done = executor.submit(() -> send(reply, "POST", apiKey, sessionId, body));
        return new Background(reply, done);
    }

    /// Answers a request the server made of the client (e.g. a form).
    ///
    /// @param apiKey    The user's API key.
    /// @param sessionId The session's id.
    /// @param requestId The id of the server's request.
    /// @param result    The answer.
    /// @return The reply.
    Reply answer(final String apiKey,
                 final String sessionId,
                 final Object requestId,
                 final Map<String, Object> result) {
        final Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", requestId);
        message.put("result", result);
        return post(apiKey, sessionId, JsonUtil.writeValueAsString(message, false));
    }

    /// Ends a session, as a client does: a DELETE.
    ///
    /// @param apiKey    The user's API key.
    /// @param sessionId The session's id.
    /// @return The reply.
    Reply delete(final String apiKey, final String sessionId) {
        return send("DELETE", apiKey, sessionId, "");
    }

    private Reply post(final String apiKey, final String sessionId, final String body) {
        return send("POST", apiKey, sessionId, body);
    }

    private Reply send(final String method, final String apiKey, final String sessionId, final String body) {
        return send(new Reply(), method, apiKey, sessionId, body);
    }

    private Reply send(final Reply reply,
                       final String method,
                       final String apiKey,
                       final String sessionId,
                       final String body) {
        try {
            securityFilter.doFilter(request(method, apiKey, sessionId, body), reply.response(),
                    (request, response) -> servlet.service(request, response));
        } catch (final Exception e) {
            throw new IllegalStateException("The request failed: " + e.getMessage(), e);
        }
        return reply;
    }

    private static String request(final int id, final String method, final Map<String, Object> params) {
        final Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("method", method);
        message.put("params", params);
        return JsonUtil.writeValueAsString(message, false);
    }

    // A request to /mcp, as the servlet container gives it.
    private static HttpServletRequest request(final String method,
                                              final String apiKey,
                                              final String sessionId,
                                              final String body) throws IOException {
        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Accept", "application/json, text/event-stream");
        headers.put("Content-Type", "application/json");
        if (apiKey != null) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        if (sessionId != null) {
            headers.put(HttpHeaders.MCP_SESSION_ID, sessionId);
        }
        final Map<String, Object> attributes = new ConcurrentHashMap<>();
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getMethod()).thenReturn(method);
        Mockito.when(request.getRequestURI()).thenReturn("/api" + McpServlet.PATH);
        Mockito.when(request.getServletPath()).thenReturn(McpServlet.PATH);
        Mockito.when(request.getHeader(Mockito.anyString())).thenAnswer(invocation ->
                headers.get(invocation.<String>getArgument(0)));
        Mockito.when(request.getContentType()).thenReturn("application/json");
        Mockito.when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        Mockito.when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        Mockito.when(request.getInputStream()).thenReturn(new BodyStream(body));
        Mockito.when(request.getAttribute(Mockito.anyString())).thenAnswer(invocation ->
                attributes.get(invocation.<String>getArgument(0)));
        Mockito.doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(request).setAttribute(Mockito.anyString(), Mockito.any());
        // The transport answers a request as a stream: an async request.
        final AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        Mockito.when(request.startAsync()).thenReturn(asyncContext);
        return request;
    }


    // --------------------------------------------------------------------------------


    /// A call made on another thread.
    ///
    /// @param reply What the client is sent, as it is sent.
    /// @param done  Done once the call has been answered.
    record Background(Reply reply, Future<?> done) {

    }

    /// What the client was sent in reply to one request.
    static final class Reply {

        private final StringWriter body = new StringWriter();
        private final Map<String, String> headers = new ConcurrentHashMap<>();
        private volatile int status = HttpServletResponse.SC_OK;
        private volatile String error;
        private final HttpServletResponse response;

        private Reply() {
            response = Mockito.mock(HttpServletResponse.class);
            try {
                Mockito.when(response.getWriter()).thenReturn(new PrintWriter(body, true));
                Mockito.doAnswer(invocation -> {
                    status = invocation.getArgument(0);
                    return null;
                }).when(response).setStatus(Mockito.anyInt());
                Mockito.doAnswer(invocation -> {
                    status = invocation.getArgument(0);
                    error = invocation.getArgument(1);
                    return null;
                }).when(response).sendError(Mockito.anyInt(), Mockito.anyString());
                Mockito.doAnswer(invocation -> {
                    status = invocation.getArgument(0);
                    return null;
                }).when(response).sendError(Mockito.anyInt());
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            Mockito.doAnswer(invocation -> {
                headers.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(response).setHeader(Mockito.anyString(), Mockito.anyString());
        }

        private HttpServletResponse response() {
            return response;
        }

        /// @return The HTTP status the client was sent.
        int status() {
            return status;
        }

        /// @return The message of an HTTP error the servlet sent, or null.
        String error() {
            return error;
        }

        /// @return The session's id, as the server gave it, or null.
        String sessionId() {
            return headers.get(HttpHeaders.MCP_SESSION_ID);
        }

        /// @return What the client was sent in the reply's body (so far, for a stream).
        String body() {
            return body.toString();
        }

        /// @return The JSON-RPC messages the client was sent: each event of a stream, or the body.
        List<Map<String, Object>> messages() {
            final String text = body();
            final List<Map<String, Object>> messages = new ArrayList<>();
            if (text.contains("data:")) {
                for (final String line : text.split("\n")) {
                    if (line.startsWith("data:")) {
                        messages.add(readMap(line.substring("data:".length()).trim()));
                    }
                }
            } else if (!text.isBlank()) {
                messages.add(readMap(text));
            }
            return messages;
        }

        /// @param method A method.
        /// @return The request (e.g. a form) the server made of the client in this reply, by that method, or null.
        Map<String, Object> serverRequest(final String method) {
            return messages().stream()
                    .filter(message -> method.equals(message.get("method")))
                    .findFirst()
                    .orElse(null);
        }

        /// @return The text of a `tools/call` answer in this reply, or null if there is none.
        String toolText() {
            for (final Map<String, Object> message : messages()) {
                if (message.get("result") instanceof final Map<?, ?> result
                    && result.get("content") instanceof final List<?> content) {
                    final StringBuilder text = new StringBuilder();
                    for (final Object item : content) {
                        if (item instanceof final Map<?, ?> part && part.get("text") != null) {
                            text.append(part.get("text"));
                        }
                    }
                    return text.toString();
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> readMap(final String json) {
            return JsonUtil.readValue(json, Map.class);
        }

        @Override
        public String toString() {
            return "Reply{status=" + status + ", error=" + error + ", body=" + body() + "}";
        }
    }

    // A request's body, as the servlet container gives it.
    private static final class BodyStream extends ServletInputStream {

        private final ByteArrayInputStream body;

        private BodyStream(final String body) {
            this.body = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public boolean isFinished() {
            return body.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            throw new UnsupportedOperationException("Not read asynchronously");
        }

        @Override
        public int read() {
            return body.read();
        }

        @Override
        public int read(final byte[] bytes, final int offset, final int length) {
            return body.read(bytes, offset, length);
        }
    }
}
