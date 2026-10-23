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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.AiToolPolicyService;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.ToolGrants;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.StroomTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCallGuard;
import stroom.ai.impl.tools.ToolProgress;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.config.common.UriFactory;
import stroom.mcp.impl.tools.FormsRequired;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/// The tools Stroom's MCP endpoint offers a session, and the calls of them: Stroom's own tools, as the chat has
/// them, under the session's user's tool policies.
///
/// A call is made as the session's user. It is refused if the tool isn't one the user's policies grant (or is
/// only for the chat), or the call changes something and the tool's category is granted only for reading. A call
/// that changes something needs a client that can show forms (MCP elicitation), and is refused otherwise (see
/// [FormsRequired]); it asks the user first (see [McpApprovals]), unless the tool asks the user itself before each
/// change ([StroomTool#confirmsItself()]). A heavy call takes one of the user's run
/// slots (see [StroomTool#usesRunSlot()]), given back while it waits for the user. A call runs within the longer of
/// the session's tool and query time limits (see [ToolDeadline]), less any time it waits for the user. Every call
/// is audited.
@Singleton
class McpTools {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpTools.class);

    private final Provider<BuiltInTools> builtInToolsProvider;
    private final Provider<AiToolPolicyService> policiesProvider;
    private final McpApprovals approvals;
    private final McpForms forms;
    private final McpWorkspaces workspaces;
    private final Provider<ChatWorkspace> chatWorkspaceProvider;
    private final Provider<AiChatRunManager> runManagerProvider;
    private final Provider<AiAuditLog> auditLogProvider;
    private final Provider<UriFactory> uriFactoryProvider;
    private final ToolCallGuard guard;

    @Inject
    McpTools(final Provider<BuiltInTools> builtInToolsProvider,
             final Provider<AiToolPolicyService> policiesProvider,
             final McpApprovals approvals,
             final McpForms forms,
             final McpWorkspaces workspaces,
             final Provider<ChatWorkspace> chatWorkspaceProvider,
             final Provider<AiChatRunManager> runManagerProvider,
             final Provider<AiAuditLog> auditLogProvider,
             final Provider<UriFactory> uriFactoryProvider,
             final ToolCallGuard guard) {
        this.builtInToolsProvider = builtInToolsProvider;
        this.policiesProvider = policiesProvider;
        this.approvals = approvals;
        this.forms = forms;
        this.workspaces = workspaces;
        this.chatWorkspaceProvider = chatWorkspaceProvider;
        this.runManagerProvider = runManagerProvider;
        this.auditLogProvider = auditLogProvider;
        this.uriFactoryProvider = uriFactoryProvider;
        this.guard = guard;
    }

    /// The tools offered to a session: those its user's policies grant, except the chat's own.
    ///
    /// A client that can't show forms is offered the tools that change things too, though their calls that would
    /// change something are refused: many of them have calls that only read (e.g. a dry run), and a refusal says
    /// why and what the user can do, where a tool left out would leave the model guessing at another way to make
    /// the change.
    ///
    /// @param session A session, whose user is the current user.
    /// @return The tools offered to it.
    List<Tool> list(final McpSession session) {
        final ToolGrants grants = policiesProvider.get().grantsFor(session.owner());
        final List<Tool> tools = new ArrayList<>();
        for (final StroomTool tool : builtInToolsProvider.get().tools()) {
            if (isOffered(tool, grants)) {
                tools.add(McpToolSchemas.toMcpTool(tool));
            }
        }
        return tools;
    }

    /// Makes a call, as the current user (the session's).
    ///
    /// @param session  The session.
    /// @param exchange The request.
    /// @param request  The call.
    /// @return What the tool returned, or why the call wasn't made.
    CallToolResult call(final McpSession session,
                        final McpAsyncServerExchange exchange,
                        final CallToolRequest request) {
        final String name = request.name();
        final String arguments = JsonUtil.writeValueAsString(NullSafe.map(request.arguments()), false);
        return call(session, exchange, request, name, arguments);
    }

    private CallToolResult call(final McpSession session,
                                final McpAsyncServerExchange exchange,
                                final CallToolRequest request,
                                final String name,
                                final String arguments) {
        final ToolGrants grants = policiesProvider.get().grantsFor(session.owner());
        final StroomTool tool = builtInToolsProvider.get().tool(name)
                .filter(found -> isOffered(found, grants))
                .orElse(null);
        if (tool == null) {
            audit(session, name, arguments, "No such tool", false);
            return error("Error: there is no tool '" + name + "'. List the tools to see what you can use.");
        }
        final ToolCall toolCall = new ToolCall(UUID.randomUUID().toString(), name, arguments);
        final boolean readOnlyCall = tool.isReadOnly(toolCall);
        if (!grants.allowsCall(tool.category(), readOnlyCall)) {
            audit(session, name, arguments, "Not allowed by tool policy", false);
            return text("Not run: your tool policies allow the " + tool.category().getDisplayValue()
                        + " tools only for calls that cannot change anything, and this call would change something. "
                        + "Ask an administrator for a policy that grants read and write.");
        }
        // A change needs the user's agreement in a form: without one, nothing is changed (no id is given to pass
        // back instead, as the model could approve its own call with it).
        if (!readOnlyCall && !session.canShowForms()) {
            audit(session, name, arguments, "Refused: the client can't show forms", false);
            return text(FormsRequired.noForms(name));
        }
        // A tool that confirms each change itself asks the user in its own form, which is the approval.
        if (!readOnlyCall && !tool.confirmsItself()) {
            final Optional<String> notApproved = approvals.approve(session, exchange, name, arguments);
            if (notApproved.isPresent()) {
                audit(session, name, arguments, "Not approved", false);
                return text(notApproved.get());
            }
        }
        final Optional<McpCallSlot> slot = tool.usesRunSlot()
                ? McpCallSlot.take(() -> runManagerProvider.get().takeToolCallSlot(session.owner().getUuid()))
                : Optional.of(McpCallSlot.none());
        if (slot.isEmpty()) {
            audit(session, name, arguments, "No free run slot", false);
            return text("Not run: you already have as many chat runs and heavy tool calls working as you may. Try "
                        + "again when one has finished.");
        }
        try (final McpCallSlot held = slot.get()) {
            final ChatWorkspace chatWorkspace = chatWorkspaceProvider.get();
            final ToolCallContext context = context(session, exchange, request, held, chatWorkspace);
            // As in the chat: arguments read leniently, and calls that keep failing stopped (see ToolCallGuard).
            final Duration limit = callTimeLimit(chatWorkspace.limits());
            final Supplier<ToolResult> work = () -> guard.run(tool, toolCall, context, tool::execute);
            // A limit of zero is taken as none.
            final ToolResult result = limit.isPositive()
                    ? ToolDeadline.run(limit, work)
                    : work.get();
            final String text = NullSafe.string(result.text());
            final boolean error = result.isError() || text.startsWith("Error");
            audit(session, name, arguments, error
                    ? "Failed"
                    : "Run", !error);
            final CallToolResult.Builder builder = CallToolResult.builder()
                    .content(List.of(new TextContent(text)))
                    .isError(error);
            // A tool's structured form (e.g. the Stroom MCP server's JSON shape of a result), beside its text.
            if (!result.structuredContent().isEmpty()) {
                builder.structuredContent(result.structuredContent());
            }
            return builder.build();
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> LogUtil.message(
                    "Tool '{}' failed in an MCP session of {}",
                    name,
                    session.owner().toDisplayString()),
                    e);
            audit(session, name, arguments, "Failed", false);
            return error("Error running tool '" + name + "': "
                         + NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
        }
    }

    /// @param limits The session's limits.
    /// @return How long a call may take in all: the longer of the tool and query time limits, as tools that run
    /// queries have the query time limit (and keep to it themselves).
    static Duration callTimeLimit(final AiWorkspaceConfig limits) {
        final AiWorkspaceConfig config = Objects.requireNonNullElseGet(limits, AiWorkspaceConfig::new);
        final Duration tool = NullSafe.getOrElse(config.getToolTimeLimit(), StroomDuration::getDuration,
                Duration.ZERO);
        final Duration query = NullSafe.getOrElse(config.getQueryTimeLimit(), StroomDuration::getDuration,
                Duration.ZERO);
        return tool.compareTo(query) >= 0
                ? tool
                : query;
    }

    private ToolCallContext context(final McpSession session,
                                    final McpAsyncServerExchange exchange,
                                    final CallToolRequest request,
                                    final McpCallSlot slot,
                                    final ChatWorkspace chatWorkspace) {
        return ToolCallContext.builder(session.toolSession())
                .workspace(new SessionWorkspace(chatWorkspace, workspaces.chatIdFor(session)))
                .userQuestions(new McpUserQuestions(session, exchange, forms, slot))
                .progress(progress(exchange, request.progressToken()))
                .limits(SessionLimits.lookedUp(chatWorkspace::limits))
                .links(StroomLinks.absolute(uriFactoryProvider.get().uiUri("").toString()))
                .build();
    }

    // Tells the client how the call is getting on, if it asked to be told.
    private static ToolProgress progress(final McpAsyncServerExchange exchange, final Object progressToken) {
        if (progressToken == null) {
            return ToolProgress.NONE;
        }
        final AtomicInteger step = new AtomicInteger();
        return message -> exchange.progressNotification(new ProgressNotification(progressToken,
                        (double) step.incrementAndGet(), null, message, null))
                .subscribe(ignored -> {
                }, e -> LOGGER.debug(() -> LogUtil.message("Unable to send progress: {}", e.getMessage()), e));
    }

    private static boolean isOffered(final StroomTool tool, final ToolGrants grants) {
        return !tool.isChatOnly() && grants.offers(tool.category(), tool.isReadOnly(), tool.hasReadOnlyCalls());
    }

    private void audit(final McpSession session,
                       final String name,
                       final String arguments,
                       final String outcome,
                       final boolean success) {
        auditLogProvider.get().toolCall(session.toolSession(), name, arguments, outcome, success, false);
    }

    private static CallToolResult text(final String text) {
        return CallToolResult.builder().content(List.of(new TextContent(text))).isError(false).build();
    }

    private static CallToolResult error(final String text) {
        return CallToolResult.builder().content(List.of(new TextContent(text))).isError(true).build();
    }
}
