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

import stroom.openai.shared.McpToolGroupRule;
import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolServerConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult;
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
import java.util.concurrent.ConcurrentHashMap;
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
    // The tool metadata key a server can use to say which group a tool is in.
    static final String META_GROUP = "group";

    static final String LIST_RESOURCES_TOOL = "list_resources";
    static final String GET_RESOURCE_TOOL = "get_resource";
    private static final String SERVER_ARG = "mcpServer";
    private static final String URI_ARG = "uri";

    private final List<McpServerConnection> connections;
    private final List<String> warnings;
    private final boolean ownsConnections;
    private final Set<String> readOnlyToolNames = ConcurrentHashMap.newKeySet();
    private final Map<String, McpToolInfo> toolInfos = new ConcurrentHashMap<>();
    // The group each tool is declared to be in, by an admin's rule or by its server.
    private final Map<String, String> declaredGroups = new ConcurrentHashMap<>();

    McpToolset(final List<McpServerConnection> connections,
               final List<String> warnings,
               final boolean ownsConnections) {
        this.connections = List.copyOf(connections);
        this.warnings = Collections.synchronizedList(new ArrayList<>(warnings));
        this.ownsConnections = ownsConnections;
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
     * Lists every server's tools, and adds the resource tools if any server has resources.
     *
     * @return The tools and how to run each of them, in the order the servers are configured.
     */
    public Map<ToolSpecification, ToolExecutor> tools() {
        final Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        final Set<String> usedNames = new HashSet<>();
        boolean anyResources = false;
        for (final McpServerConnection connection : connections) {
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
                    final String name = McpToolNames.prefixedName(
                            connection.toolNamePrefix(), tool.name(), usedNames);
                    usedNames.add(name);
                    final ToolSpecification specification = McpToolAdapter.toToolSpecification(tool)
                            .toBuilder()
                            .name(name)
                            .build();
                    tools.put(specification, (request, memoryId) ->
                            callTool(connection, tool.name(), request.arguments()));
                    if (isReadOnly(connection, tool)) {
                        readOnlyToolNames.add(name);
                    }
                    toolInfos.put(name, toolInfo(connection, tool, name, filter.policyFor(tool.name())));
                    NullSafe.consume(declaredGroup(filter, tool), group -> declaredGroups.put(name, group));
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
     * @return The group an admin's rule, or the tool's server (as {@code _meta.group}), puts the tool in, which
     * may be {@link McpToolGroupRule#ALWAYS_OFFERED}; or empty if neither does.
     */
    public Optional<String> declaredGroup(final String toolName) {
        return Optional.ofNullable(declaredGroups.get(toolName));
    }

    private static String declaredGroup(final McpToolFilter filter, final Tool tool) {
        final String ruleGroup = filter.groupFor(tool.name());
        if (ruleGroup != null) {
            return ruleGroup;
        }
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
                policy,
                McpToolInfo.hash(endpoint, String.valueOf(trusted), tool.name(), tool.description(),
                        String.valueOf(tool.inputSchema())),
                McpToolInfo.hash(endpoint, String.valueOf(trusted)));
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

    private static String callTool(final McpServerConnection connection,
                                   final String toolName,
                                   final String argumentsJson) {
        final Map<String, Object> args = McpToolAdapter.parseArguments(argumentsJson);
        return McpToolAdapter.renderResult(connection.client().callTool(new CallToolRequest(toolName, args)));
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
}
