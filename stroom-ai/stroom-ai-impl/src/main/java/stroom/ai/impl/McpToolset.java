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

import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolServerConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The MCP servers available to a single "Ask Stroom AI" question. The toolset offers each server's tools to
 * the agent loop, each name prefixed with its server (see {@link McpToolNames}) and filtered by the server's
 * allowed and denied tools, plus two tools for listing and reading MCP resources when any server has them.
 * <p>
 * A server that cannot be reached, or whose tools cannot be listed, is left out rather than failing the
 * question; the reason is recorded in {@link #getWarnings()}.
 * <p>
 * Closing the toolset closes the connections it owns. Connections taken from {@link McpClientCache} are left
 * open for the chat's next question.
 */
public final class McpToolset implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpToolset.class);

    public static final McpToolset EMPTY = new McpToolset(List.of(), List.of(), true);
    /// The key, in a tool call's `_meta`, of the model calling the tool, so a server can record it (e.g. in a
    /// version history) rather than relying on the model to say which it is, which models often get wrong.
    static final String MODEL_META_KEY = "stroom.ai/model";
    // The tool metadata key a server can use to say which group a tool is in.
    static final String META_GROUP = "group";
    static final String META_CONFIRMS = "confirms";
    static final String USER_ACTIONS = "user_actions";
    /// The most of one server's instructions given to the model, so that a server cannot fill the context.
    static final int MAX_SERVER_INSTRUCTIONS_CHARS = 20_000;

    static final String LIST_RESOURCES_TOOL = "list_resources";
    static final String GET_RESOURCE_TOOL = "get_resource";
    private static final String SERVER_ARG = "mcpServer";
    private static final String URI_ARG = "uri";

    private final List<McpServerConnection> connections;
    private final List<String> warnings;
    private final boolean ownsConnections;
    // The model the tools are called for, or null.
    private final String modelId;
    private final Set<String> readOnlyToolNames = ConcurrentHashMap.newKeySet();
    private final Map<String, McpToolInfo> toolInfos = new ConcurrentHashMap<>();
    private final Map<String, Object> userActions = new ConcurrentHashMap<>();
    // How many times each server had said its tools changed when they were last listed (see toolsChanged).
    private final Map<McpServerConnection, Long> toolListVersions = new ConcurrentHashMap<>();
    // Who is told what each tool call under way has got to, by the model's id for the call.
    private final Map<String, Consumer<String>> progressListeners = new ConcurrentHashMap<>();
    // The group each tool is declared to be in, by an admin's rule or by its server.
    private final Map<String, String> declaredGroups = new ConcurrentHashMap<>();

    McpToolset(final List<McpServerConnection> connections,
               final List<String> warnings,
               final boolean ownsConnections) {
        this(connections, warnings, ownsConnections, null);
    }

    /// @param modelId The model the tools are called for, as its model document has it, told to each server with
    ///                each call (see [#MODEL_META_KEY]), or null.
    McpToolset(final List<McpServerConnection> connections,
               final List<String> warnings,
               final boolean ownsConnections,
               final String modelId) {
        this.connections = List.copyOf(connections);
        this.warnings = Collections.synchronizedList(new ArrayList<>(warnings));
        this.ownsConnections = ownsConnections;
        this.modelId = NullSafe.isNonBlankString(modelId)
                ? modelId.strip()
                : null;
    }

    /**
     * @return true if there are no MCP tool servers to use.
     */
    public boolean isEmpty() {
        return connections.isEmpty();
    }

    /**
     * @return Why servers, or their tools, have been left out of this question, one line each.
     */
    public List<String> getWarnings() {
        synchronized (warnings) {
            return List.copyOf(warnings);
        }
    }

    /**
     * @return The connected servers, in the order they are configured.
     */
    List<McpServerConnection> getConnections() {
        return connections;
    }

    /**
     * The instructions that trusted servers give, when connected, about how to use them, e.g. the order to do
     * things in. An untrusted server's instructions are left out, as they could tell the model anything.
     *
     * @param servers The servers to include, by label: those whose tools the model is offered.
     * @return The instructions, under a heading per server, or null if no such trusted server gives any.
     */
    public String serverInstructions(final Set<String> servers) {
        final StringBuilder sb = new StringBuilder();
        for (final McpServerConnection connection : connections) {
            if (!servers.contains(connection.label())
                || connection.config() == null
                || !connection.config().isTrusted()) {
                continue;
            }
            final String instructions;
            try {
                instructions = connection.instructions();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to read the instructions of MCP server '" + connection.label() + "'", e);
                continue;
            }
            if (NullSafe.isBlankString(instructions)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("## Instructions from MCP server ").append(connection.label()).append("\n\n");
            if (instructions.length() > MAX_SERVER_INSTRUCTIONS_CHARS) {
                sb.append(instructions, 0, MAX_SERVER_INSTRUCTIONS_CHARS)
                        .append("\n[The rest of these instructions has been left out, as they are too long.]");
            } else {
                sb.append(instructions.strip());
            }
        }
        return sb.isEmpty()
                ? null
                : sb.toString();
    }

    /**
     * Lists every server's tools, and adds the resource tools if any server has resources.
     *
     * @return The tools and how to run each of them, in the order the servers are configured.
     */
    public Map<ToolSpecification, ToolExecutor> tools() {
        return tools(Set.of());
    }

    /// @return True if any of the servers is not trusted.
    public boolean hasUntrustedServer() {
        return connections.stream()
                .anyMatch(connection ->
                        !NullSafe.getOrElse(connection.config(), McpToolServerConfig::isTrusted, false));
    }

    /// Lists every server's tools as the servers give them, including any a tool policy denies, so an
    /// administrator can look through them.
    ///
    /// @return Each tool with its server and the policy that applies to it, in the order the servers are
    /// configured.
    /// @throws RuntimeException if a server's tools can't be listed.
    public List<ServerTool> listServerTools() {
        final List<ServerTool> listed = new ArrayList<>();
        for (final McpServerConnection connection : connections) {
            final McpToolFilter filter = McpToolFilter.forServer(connection.config());
            for (final Tool tool : connection.tools()) {
                listed.add(new ServerTool(connection.config(), tool, filter.policyFor(tool.name())));
            }
        }
        return listed;
    }

    /**
     * Lists every server's tools, and adds the resource tools if any server has resources.
     *
     * @param reservedNames Names the tools must not be given, e.g. those of the built-in tools.
     * @return The tools and how to run each of them, in the order the servers are configured.
     */
    public Map<ToolSpecification, ToolExecutor> tools(final Set<String> reservedNames) {
        // Listed afresh, so what is known of tools a server no longer has goes.
        readOnlyToolNames.clear();
        toolInfos.clear();
        declaredGroups.clear();
        final Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        final Set<String> usedNames = new HashSet<>(reservedNames);
        boolean anyResources = false;
        for (final McpServerConnection connection : connections) {
            toolListVersions.put(connection, connection.toolListVersion());
            final List<Tool> serverTools;
            try {
                serverTools = connection.tools();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to list tools of MCP server '" + connection.label() + "'", e);
                warnings.add("The tools of MCP tool server '" + connection.label() + "' could not be listed - "
                             + NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
                continue;
            }
            final McpToolFilter filter = McpToolFilter.forServer(connection.config());
            for (final Tool tool : serverTools) {
                if (filter.isAllowed(tool.name())) {
                    final String name = McpToolNames.modelName(
                            connection.toolNamePrefix(), tool.name(), usedNames);
                    usedNames.add(name);
                    final ToolSpecification specification = McpToolAdapter.toToolSpecification(tool)
                            .toBuilder()
                            .name(name)
                            .build();
                    tools.put(specification, (request, memoryId) ->
                            callTool(connection, tool.name(), request));
                    if (isReadOnly(connection, tool)) {
                        readOnlyToolNames.add(name);
                    }
                    toolInfos.put(name, toolInfo(connection, tool, name, filter.policyFor(tool.name())));
                    NullSafe.consume(declaredGroup(tool), group -> declaredGroups.put(name, group));
                } else {
                    LOGGER.debug(() -> "Tool '" + tool.name() + "' of MCP server '" + connection.label()
                                       + "' is not allowed");
                }
            }
            anyResources = anyResources || hasResources(connection.client());
        }
        if (anyResources) {
            tools.put(listResourcesSpecification(), (request, memoryId) -> listResources(request.arguments()));
            tools.put(getResourceSpecification(), (request, memoryId) -> getResource(request.arguments()));
            readOnlyToolNames.add(LIST_RESOURCES_TOOL);
            readOnlyToolNames.add(GET_RESOURCE_TOOL);
        }
        return tools;
    }

    /**
     * @param toolName The name of a tool offered by {@link #tools()}.
     * @return True if the tool cannot change anything. An MCP tool only counts as read-only if its server is
     * trusted and the tool is annotated as read-only, as annotations from an untrusted server are only hints.
     * Reading resources is read-only.
     */
    public boolean isReadOnly(final String toolName) {
        return readOnlyToolNames.contains(toolName);
    }

    /**
     * @param toolName The name of a tool offered by {@link #tools()}.
     * @return What is known about it, if it is a server's tool (not one of the resource tools).
     */
    public Optional<McpToolInfo> toolInfo(final String toolName) {
        return Optional.ofNullable(toolInfos.get(toolName));
    }

    /**
     * @param toolName The name of a tool offered by {@link #tools()}.
     * @return The group the tool's server puts it in (as {@code _meta.group}), or empty if it does not.
     */
    public Optional<String> declaredGroup(final String toolName) {
        return Optional.ofNullable(declaredGroups.get(toolName));
    }

    /// @param tool A tool, as its server gives it.
    /// @return The group its server puts it in (as `_meta.group`), or null if it does not.
    static String declaredGroup(final Tool tool) {
        final Object metaGroup = NullSafe.get(tool.meta(), meta -> meta.get(META_GROUP));
        return metaGroup instanceof final String group && !group.isBlank()
                ? group.strip()
                : null;
    }

    private static McpToolInfo toolInfo(final McpServerConnection connection,
                                        final Tool tool,
                                        final String prefixedName,
                                        final McpToolPolicy policy) {
        final boolean trusted = connection.config() != null && connection.config().isTrusted();
        final String endpoint = NullSafe.get(connection.config(), McpToolServerConfig::getEndpoint);
        return new McpToolInfo(
                prefixedName,
                connection.label(),
                tool.name(),
                trusted,
                NullSafe.get(tool.annotations(), ToolAnnotations::readOnlyHint),
                NullSafe.get(tool.annotations(), ToolAnnotations::destructiveHint),
                NullSafe.get(tool.annotations(), ToolAnnotations::openWorldHint),
                Boolean.TRUE.equals(NullSafe.get(tool.meta(), meta -> meta.get(META_CONFIRMS))),
                policy,
                McpToolInfo.hash(endpoint, String.valueOf(trusted), tool.name(), tool.description(),
                        String.valueOf(tool.inputSchema())),
                McpToolInfo.hash(endpoint, String.valueOf(trusted)),
                tool.description());
    }

    static boolean isReadOnly(final McpServerConnection connection, final Tool tool) {
        return connection.config() != null
               && connection.config().isTrusted()
               && tool.annotations() != null
               && Boolean.TRUE.equals(tool.annotations().readOnlyHint());
    }

    private static List<Resource> listResources(final McpSyncClient client) {
        final List<Resource> resources = new ArrayList<>();
        String cursor = null;
        do {
            final ListResourcesResult result = cursor == null
                    ? client.listResources()
                    : client.listResources(cursor);
            resources.addAll(NullSafe.list(result.resources()));
            cursor = result.nextCursor();
        } while (NullSafe.isNonBlankString(cursor));
        return resources;
    }

    /**
     * Lists the prompts every server offers. A server whose prompts cannot be listed is left out.
     *
     * @return Each server's prompts, in the order the servers are configured.
     */
    public List<ServerPrompt> prompts() {
        final List<ServerPrompt> prompts = new ArrayList<>();
        for (final McpServerConnection connection : connections) {
            final ServerCapabilities capabilities = connection.client().getServerCapabilities();
            if (capabilities == null || capabilities.prompts() == null) {
                continue;
            }
            try {
                String cursor = null;
                do {
                    final ListPromptsResult result = cursor == null
                            ? connection.client().listPrompts()
                            : connection.client().listPrompts(cursor);
                    NullSafe.list(result.prompts()).forEach(prompt ->
                            prompts.add(new ServerPrompt(connection.label(), prompt)));
                    cursor = result.nextCursor();
                } while (NullSafe.isNonBlankString(cursor));
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to list the prompts of MCP server '" + connection.label() + "'", e);
            }
        }
        return prompts;
    }

    /**
     * Gets a prompt from a server, filled in with arguments.
     *
     * @param server    The server's name.
     * @param name      The prompt's name.
     * @param arguments The prompt's arguments.
     * @return The prompt's text: the text of its messages, separated by blank lines.
     */
    public String getPrompt(final String server, final String name, final Map<String, String> arguments) {
        final McpServerConnection connection = connections.stream()
                .filter(candidate -> candidate.label().equals(server))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("There is no MCP tool server '" + server + "'."));
        final GetPromptResult result = connection.client().getPrompt(
                new GetPromptRequest(name, new LinkedHashMap<>(NullSafe.map(arguments))));
        return NullSafe.list(result.messages()).stream()
                .map(PromptMessage::content)
                .map(content -> content instanceof final TextContent text
                        ? text.text()
                        : "[" + content.type() + " content left out]")
                .collect(Collectors.joining("\n\n"));
    }

    private static boolean hasResources(final McpSyncClient client) {
        final ServerCapabilities capabilities = client.getServerCapabilities();
        return capabilities != null && capabilities.resources() != null;
    }

    private String callTool(final McpServerConnection connection,
                            final String toolName,
                            final ToolExecutionRequest request) {
        final Map<String, Object> args = McpToolAdapter.parseArguments(request.arguments());
        final Consumer<String> listener = request.id() != null
                ? progressListeners.get(request.id())
                : null;
        final CallToolRequest.Builder call = CallToolRequest.builder()
                .name(toolName)
                .arguments(args);
        if (modelId != null) {
            // Which model is calling, with every call, as the user may change it during a chat. Mutable, as the
            // progress token goes in it too.
            final Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(MODEL_META_KEY, modelId);
            call.meta(meta);
        }
        final CallToolResult result;
        if (listener == null) {
            result = connection.client().callTool(call.build());
        } else {
            // A server only reports a call's progress if it is given a token for it, unique to the call.
            final String progressToken = UUID.randomUUID().toString();
            final ProgressOrder order = new ProgressOrder();
            connection.listen(progressToken, notification -> {
                if (order.isNewer(notification)) {
                    NullSafe.consume(describeProgress(notification), listener);
                }
            });
            try {
                result = connection.client().callTool(call.progressToken(progressToken).build());
            } finally {
                connection.stopListening(progressToken);
            }
        }
        // Steps that are the user's, to show as buttons. Only a trusted server's are kept, and they are checked
        // before they are shown.
        if (request.id() != null
            && connection.config() != null
            && connection.config().isTrusted()
            && result != null
            && result.structuredContent() instanceof final Map<?, ?> structured
            && structured.get(USER_ACTIONS) != null) {
            userActions.put(request.id(), structured.get(USER_ACTIONS));
        }
        return McpToolAdapter.renderResult(result);
    }

    /// @return The servers that have said their tools have changed since [#tools(Set)] last listed them, by name,
    /// empty if none have.
    public List<String> serversWithChangedTools() {
        final List<String> changed = new ArrayList<>();
        for (final McpServerConnection connection : connections) {
            final Long listed = toolListVersions.get(connection);
            if (listed != null && listed != connection.toolListVersion()) {
                changed.add(connection.label());
            }
        }
        return changed;
    }

    /// Has what a tool call has got to, as its server reports it, passed on while the call runs, until
    /// [#stopListening(String)].
    ///
    /// @param callId   The model's id for the call.
    /// @param listener Told what the call has got to, e.g. "Reading documents (2 of 5)".
    public void listenForProgress(final String callId, final Consumer<String> listener) {
        progressListeners.put(callId, listener);
    }

    /// Stops passing on what a tool call has got to.
    ///
    /// @param callId The model's id for the call.
    public void stopListening(final String callId) {
        progressListeners.remove(callId);
    }

    /// @param notification Progress a server reported.
    /// @return What it says, e.g. "Reading documents (2 of 5)", "Reading documents (40%)" or "2 of 5", or null if
    /// it says nothing that can be shown.
    static String describeProgress(final ProgressNotification notification) {
        final String message = NullSafe.trim(notification.message());
        final Double progress = notification.progress();
        final Double total = notification.total();
        final String howFar;
        if (progress == null || progress < 0) {
            howFar = null;
        } else if (total != null && total > 0) {
            howFar = isWhole(progress) && isWhole(total)
                    ? (long) progress.doubleValue() + " of " + (long) total.doubleValue()
                    : Math.round(Math.min(100, 100 * progress / total)) + "%";
        } else {
            // A count with nothing to say what of only means something beside a message.
            howFar = null;
        }
        if (message.isEmpty()) {
            return howFar;
        }
        return howFar != null
                ? message + " (" + howFar + ")"
                : message;
    }

    private static boolean isWhole(final double value) {
        return value == Math.rint(value) && !Double.isInfinite(value);
    }

    /**
     * @param callId The model's id for a tool call.
     * @return The user actions the call's result gave, as the server sent them (unchecked), or null if it gave
     * none. They are forgotten once taken.
     */
    public Object takeUserActions(final String callId) {
        return callId == null
                ? null
                : userActions.remove(callId);
    }

    private String listResources(final String argumentsJson) {
        final Map<String, Object> args = McpToolAdapter.parseArguments(argumentsJson);
        final String serverName = asString(args.get(SERVER_ARG));
        final StringBuilder sb = new StringBuilder();
        for (final McpServerConnection connection : connections) {
            if ((serverName == null || serverName.equals(connection.label())) && hasResources(connection.client())) {
                sb.append("Resources on MCP server '").append(connection.label()).append("':\n")
                        .append(McpToolAdapter.renderResourceList(listResources(connection.client())))
                        .append("\n\n");
            }
        }
        if (sb.isEmpty()) {
            return serverName != null
                    ? "No MCP server with resources called '" + serverName + "'"
                    : "No MCP server with resources";
        }
        return sb.toString().strip();
    }

    private String getResource(final String argumentsJson) {
        final Map<String, Object> args = McpToolAdapter.parseArguments(argumentsJson);
        final String serverName = asString(args.get(SERVER_ARG));
        final String uri = asString(args.get(URI_ARG));
        if (uri == null) {
            return "Error: the '" + URI_ARG + "' argument is required";
        }
        for (final McpServerConnection connection : connections) {
            if ((serverName == null || serverName.equals(connection.label())) && hasResources(connection.client())) {
                return McpToolAdapter.renderResource(
                        connection.client().readResource(new ReadResourceRequest(uri)));
            }
        }
        return "Error: no MCP server with resources called '" + serverName + "'";
    }

    private ToolSpecification listResourcesSpecification() {
        return ToolSpecification.builder()
                .name(LIST_RESOURCES_TOOL)
                .description("Lists the resources (documents, guides etc.) available from the MCP servers. "
                             + "Read one with " + GET_RESOURCE_TOOL + ".")
                .parameters(JsonObjectSchema.builder()
                        .addEnumProperty(SERVER_ARG, serverLabels(),
                                "Only list the resources of this MCP server. Leave out to list them all.")
                        .build())
                .build();
    }

    private ToolSpecification getResourceSpecification() {
        return ToolSpecification.builder()
                .name(GET_RESOURCE_TOOL)
                .description("Reads a resource from an MCP server, by its URI as given by " + LIST_RESOURCES_TOOL
                             + ".")
                .parameters(JsonObjectSchema.builder()
                        .addEnumProperty(SERVER_ARG, serverLabels(), "The MCP server the resource is on.")
                        .addStringProperty(URI_ARG, "The URI of the resource.")
                        .required(SERVER_ARG, URI_ARG)
                        .build())
                .build();
    }

    private List<String> serverLabels() {
        return connections.stream()
                .map(McpServerConnection::label)
                .toList();
    }

    private static String asString(final Object value) {
        return value == null || String.valueOf(value).isBlank()
                ? null
                : String.valueOf(value);
    }

    @Override
    public void close() {
        if (ownsConnections) {
            connections.forEach(McpServerConnection::close);
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * A prompt a server offers.
     *
     * @param server The server's name.
     * @param prompt The prompt.
     */
    public record ServerPrompt(String server, Prompt prompt) {

    }

    /// A server's tool, as the server gives it.
    ///
    /// @param server The server's settings.
    /// @param tool   The tool.
    /// @param policy The tool policy that applies to it, or null if none does.
    public record ServerTool(McpToolServerConfig server, Tool tool, McpToolPolicy policy) {

    }

    /// Keeps to the order a server reported a call's progress in. The MCP client passes each notification on in a
    /// thread of its own, so two sent close together can arrive the wrong way round; as a call's progress must go
    /// up with each notification, one that doesn't is older than one already shown, and is dropped.
    static final class ProgressOrder {

        private double highest = Double.NEGATIVE_INFINITY;

        /// @param notification Progress a server reported for the call.
        /// @return True if it is newer than any seen before, so is to be shown.
        synchronized boolean isNewer(final ProgressNotification notification) {
            final Double progress = notification.progress();
            if (progress == null || progress <= highest) {
                return false;
            }
            highest = progress;
            return true;
        }
    }
}
