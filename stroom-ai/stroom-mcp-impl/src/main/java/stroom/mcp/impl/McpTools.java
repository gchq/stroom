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
import stroom.ai.impl.AiChatRunManager.RunSlot;
import stroom.ai.impl.AiToolPolicyService;
import stroom.ai.impl.ToolGrants;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.StroomTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolProgress;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.config.common.UriFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/// The tools Stroom's MCP endpoint offers a session, and the calls of them: Stroom's own tools, as the chat has
/// them, under the session's user's tool policies.
///
/// A call is made as the session's user. It is refused if the tool isn't one the user's policies grant (or is
/// only for the chat), or the call changes something and the tool's category is granted only for reading. A call
/// that changes something asks the user first (see [McpApprovals]), and a heavy one takes one of the user's run
/// slots (see [StroomTool#usesRunSlot()]). Every call is audited.
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

    @Inject
    McpTools(final Provider<BuiltInTools> builtInToolsProvider,
             final Provider<AiToolPolicyService> policiesProvider,
             final McpApprovals approvals,
             final McpForms forms,
             final McpWorkspaces workspaces,
             final Provider<ChatWorkspace> chatWorkspaceProvider,
             final Provider<AiChatRunManager> runManagerProvider,
             final Provider<AiAuditLog> auditLogProvider,
             final Provider<UriFactory> uriFactoryProvider) {
        this.builtInToolsProvider = builtInToolsProvider;
        this.policiesProvider = policiesProvider;
        this.approvals = approvals;
        this.forms = forms;
        this.workspaces = workspaces;
        this.chatWorkspaceProvider = chatWorkspaceProvider;
        this.runManagerProvider = runManagerProvider;
        this.auditLogProvider = auditLogProvider;
        this.uriFactoryProvider = uriFactoryProvider;
    }

    /// @param session A session, whose user is the current user.
    /// @return The tools offered to it: those its user's policies grant, except the chat's own, and (for a client
    /// that can't show forms, if it is offered anything that changes things) [McpApprovals#APPROVE_TOOL].
    List<Tool> list(final McpSession session) {
        final ToolGrants grants = policiesProvider.get().grantsFor(session.owner());
        final List<Tool> tools = new ArrayList<>();
        boolean anyChanges = false;
        for (final StroomTool tool : builtInToolsProvider.get().tools()) {
            if (isOffered(tool, grants)) {
                tools.add(McpToolSchemas.toMcpTool(tool));
                anyChanges = anyChanges || !tool.isReadOnly();
            }
        }
        if (anyChanges && !session.canShowForms()) {
            tools.add(approveTool());
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
        if (McpApprovals.APPROVE_TOOL.equals(name)) {
            return approved(session, exchange, request, arguments);
        }
        return call(session, exchange, request, name, arguments, false);
    }

    // Makes a call the user has approved through an approval id.
    private CallToolResult approved(final McpSession session,
                                    final McpAsyncServerExchange exchange,
                                    final CallToolRequest request,
                                    final String arguments) {
        final Object approvalId = NullSafe.map(request.arguments()).get(McpApprovals.APPROVAL_ID_ARG);
        final Optional<McpApprovals.PendingCall> pending = approvals.take(session, approvalId == null
                ? null
                : String.valueOf(approvalId));
        if (pending.isEmpty()) {
            audit(session, McpApprovals.APPROVE_TOOL, arguments, "No such approval", false);
            return error("Error: there is no call waiting for approval with that " + McpApprovals.APPROVAL_ID_ARG
                         + ", or it has expired or been used. Make the call again to get a new one.");
        }
        return call(session, exchange, request, pending.get().toolName(), pending.get().arguments(), true);
    }

    private CallToolResult call(final McpSession session,
                                final McpAsyncServerExchange exchange,
                                final CallToolRequest request,
                                final String name,
                                final String arguments,
                                final boolean alreadyApproved) {
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
            return text("Not run: your tool policies allow only the " + tool.category().getDisplayValue()
                        + " tools and calls that cannot change anything, and this call would change something. "
                        + "Ask an administrator for a policy that grants read and write.");
        }
        if (!readOnlyCall && !alreadyApproved) {
            final Optional<String> notApproved = approvals.approve(session, exchange, name, arguments);
            if (notApproved.isPresent()) {
                audit(session, name, arguments, "Not approved", false);
                return text(notApproved.get());
            }
        }
        final Optional<RunSlot> slot = tool.usesRunSlot()
                ? runManagerProvider.get().takeToolCallSlot(session.owner().getUuid())
                : Optional.of(() -> {
                });
        if (slot.isEmpty()) {
            audit(session, name, arguments, "No free run slot", false);
            return text("Not run: you already have as many chat runs and heavy tool calls working as you may. Try "
                        + "again when one has finished.");
        }
        try (final RunSlot ignored = slot.get()) {
            final ToolResult result = tool.execute(toolCall, context(session, exchange, request));
            final String text = NullSafe.string(result.text());
            final boolean error = result.isError() || text.startsWith("Error");
            audit(session, name, arguments, error
                    ? "Failed"
                    : "Run", !error);
            return CallToolResult.builder()
                    .content(List.of(new TextContent(text)))
                    .isError(error)
                    .build();
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Tool '" + name + "' failed in MCP session " + session.id(), e);
            audit(session, name, arguments, "Failed", false);
            return error("Error running tool '" + name + "': "
                         + NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
        }
    }

    private ToolCallContext context(final McpSession session,
                                    final McpAsyncServerExchange exchange,
                                    final CallToolRequest request) {
        final ChatWorkspace chatWorkspace = chatWorkspaceProvider.get();
        return ToolCallContext.builder(session.toolSession())
                .workspace(new SessionWorkspace(chatWorkspace, workspaces.chatIdFor(session)))
                .userQuestions(new McpUserQuestions(session, exchange, forms))
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
                }, e -> LOGGER.debug(() -> "Unable to send progress: " + e.getMessage(), e));
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

    private static Tool approveTool() {
        return Tool.builder()
                .name(McpApprovals.APPROVE_TOOL)
                .description("Makes a call of one of Stroom's tools that the user has approved. A call that changes "
                             + "something in Stroom isn't made at once: it gives an approval id, and what to show "
                             + "the user. Only once they agree, call this with the id. Never approve a call "
                             + "yourself.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(McpApprovals.APPROVAL_ID_ARG, Map.of(
                                "type", "string",
                                "description", "The approval id the call gave.")),
                        "required", List.of(McpApprovals.APPROVAL_ID_ARG)))
                .annotations(new ToolAnnotations(null, false, true, null, false, null))
                .build();
    }

    private static CallToolResult text(final String text) {
        return CallToolResult.builder().content(List.of(new TextContent(text))).isError(false).build();
    }

    private static CallToolResult error(final String text) {
        return CallToolResult.builder().content(List.of(new TextContent(text))).isError(true).build();
    }
}
