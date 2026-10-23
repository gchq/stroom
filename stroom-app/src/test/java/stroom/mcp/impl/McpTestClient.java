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
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/// An MCP client of Stroom's REAL tools, as a session of `/mcp` has them ([McpTools]): its calls are made as its
/// user, under the user's real tool policies and permissions. Only the transport is left out: the client's
/// requests are made on [McpTools] directly, and the forms Stroom shows the user (MCP elicitation) are answered by a
/// stand-in for the user, who agrees to everything unless told otherwise.
public final class McpTestClient {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpTestClient.class);

    private final McpTools mcpTools;
    private final SecurityContext securityContext;
    private final McpSession session;
    private final McpAsyncServerExchange exchange = Mockito.mock(McpAsyncServerExchange.class);
    private final List<ElicitFormRequest> forms = Collections.synchronizedList(new ArrayList<>());
    private Function<ElicitFormRequest, ElicitResult> user = McpTestClient::agree;

    /// @param mcpTools        The endpoint's tools.
    /// @param securityContext Runs each call as the user.
    /// @param owner           The session's user.
    /// @param canShowForms    True if the client says it can show forms (MCP elicitation).
    McpTestClient(final McpTools mcpTools,
                  final SecurityContext securityContext,
                  final UserRef owner,
                  final boolean canShowForms) {
        this.mcpTools = mcpTools;
        this.securityContext = securityContext;
        final ClientCapabilities.Builder capabilities = ClientCapabilities.builder();
        if (canShowForms) {
            capabilities.elicitation();
        }
        this.session = new McpSession(UUID.randomUUID().toString(), owner, new Implementation("Test client", "1"),
                capabilities.build());
        Mockito.when(exchange.createElicitation(Mockito.any())).thenAnswer(invocation -> {
            final ElicitFormRequest form = invocation.getArgument(0);
            forms.add(form);
            LOGGER.info(() -> LogUtil.message("Form: {}", form.message()));
            return Mono.just(user.apply(form));
        });
    }

    /// @param user How the user answers each form from now on.
    public void answeringWith(final Function<ElicitFormRequest, ElicitResult> user) {
        this.user = user;
    }

    /// @return Every form the user has been shown, in order.
    public List<ElicitFormRequest> forms() {
        synchronized (forms) {
            return List.copyOf(forms);
        }
    }

    /// @return The tools the session is offered (`tools/list`).
    public List<String> toolNames() {
        return securityContext.asUserResult(session.owner(), () -> mcpTools.list(session)).stream()
                .map(Tool::name)
                .toList();
    }

    /// Calls a tool (`tools/call`).
    ///
    /// @param tool      The tool's name.
    /// @param arguments Its arguments.
    /// @return Its result.
    public CallToolResult call(final String tool, final Map<String, Object> arguments) {
        final CallToolRequest request = new CallToolRequest(tool, arguments);
        final CallToolResult result = securityContext.asUserResult(session.owner(),
                () -> mcpTools.call(session, exchange, request));
        LOGGER.info(() -> LogUtil.message("{} {} -> {}", tool, arguments, abbreviated(text(result))));
        return result;
    }

    /// Calls a tool that answers in JSON, and fails if it refuses or fails.
    ///
    /// @param tool      The tool's name.
    /// @param arguments Its arguments.
    /// @return Its result, as JSON.
    @SuppressWarnings("unchecked")
    public Map<String, Object> json(final String tool, final Map<String, Object> arguments) {
        final CallToolResult result = call(tool, arguments);
        final String text = text(result);
        if (Boolean.TRUE.equals(result.isError())) {
            throw new AssertionError(tool + " failed: " + text);
        }
        if (result.structuredContent() instanceof final Map<?, ?> structured) {
            return (Map<String, Object>) structured;
        }
        try {
            return JsonUtil.readValue(text, Map.class);
        } catch (final RuntimeException e) {
            throw new AssertionError(tool + " did not answer in JSON: " + text, e);
        }
    }

    /// @param result A tool's result.
    /// @return Its text.
    public static String text(final CallToolResult result) {
        final StringBuilder sb = new StringBuilder();
        for (final Object content : NullSafe.list(result.content())) {
            if (content instanceof final TextContent text) {
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

    /// The user agreeing to a form: yes to a yes or no, the proposal where a value is proposed, and the first of
    /// the choices offered.
    ///
    /// @param form The form.
    /// @return The user's answer.
    public static ElicitResult agree(final ElicitFormRequest form) {
        final Map<String, Object> content = new LinkedHashMap<>();
        final Object properties = NullSafe.map(form.requestedSchema()).get("properties");
        if (properties instanceof final Map<?, ?> fields) {
            fields.forEach((name, field) -> {
                final Object answer = answer(field);
                if (answer != null) {
                    content.put(String.valueOf(name), answer);
                }
            });
        }
        return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
    }

    /// The user declining a form.
    ///
    /// @param form The form.
    /// @return The user's answer.
    public static ElicitResult decline(final ElicitFormRequest form) {
        return new ElicitResult(ElicitResult.Action.DECLINE, null, null);
    }

    private static Object answer(final Object field) {
        if (!(field instanceof final Map<?, ?> schema)) {
            return null;
        }
        if ("boolean".equals(schema.get("type"))) {
            return true;
        }
        if (schema.get("default") != null) {
            return schema.get("default");
        }
        if (schema.get("enum") instanceof final List<?> options && !options.isEmpty()) {
            return options.getFirst();
        }
        return null;
    }

    private static String abbreviated(final String text) {
        return text.length() > 4_000
                ? text.substring(0, 4_000) + "…"
                : text;
    }
}
