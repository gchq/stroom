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

import stroom.openai.shared.McpToolServerConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The MCP clients built for a single "Ask Stroom AI" question, one per enabled tool server. The toolset
 * offers each server's tools to the agent loop, plus two tools for listing and reading MCP resources when
 * any server has resources. Closing the toolset closes every client (and its HTTP connection); a
 * per-client failure to close is logged and does not stop the others closing.
 */
public final class McpToolset implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpToolset.class);

    public static final McpToolset EMPTY = new McpToolset(List.of());

    static final String LIST_RESOURCES_TOOL = "list_resources";
    static final String GET_RESOURCE_TOOL = "get_resource";
    private static final String SERVER_ARG = "mcpServer";
    private static final String URI_ARG = "uri";

    private final List<McpServerConnection> connections;

    McpToolset(final List<McpServerConnection> connections) {
        this.connections = List.copyOf(connections);
    }

    /**
     * @return true if there are no MCP tool servers to use.
     */
    public boolean isEmpty() {
        return connections.isEmpty();
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
        boolean anyResources = false;
        for (final McpServerConnection connection : connections) {
            for (final Tool tool : listTools(connection.client())) {
                tools.put(McpToolAdapter.toToolSpecification(tool),
                        (request, memoryId) -> callTool(connection, tool.name(), request.arguments()));
            }
            anyResources = anyResources || hasResources(connection.client());
        }
        if (anyResources) {
            tools.put(listResourcesSpecification(), (request, memoryId) -> listResources(request.arguments()));
            tools.put(getResourceSpecification(), (request, memoryId) -> getResource(request.arguments()));
        }
        return tools;
    }

    private static List<Tool> listTools(final McpSyncClient client) {
        final List<Tool> tools = new ArrayList<>();
        String cursor = null;
        do {
            final ListToolsResult result = cursor == null
                    ? client.listTools()
                    : client.listTools(cursor);
            tools.addAll(NullSafe.list(result.tools()));
            cursor = result.nextCursor();
        } while (NullSafe.isNonBlankString(cursor));
        return tools;
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
        return sb.isEmpty()
                ? "No MCP server with resources" + (serverName != null
                ? " called '" + serverName + "'"
                : "")
                : sb.toString().strip();
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
        for (final McpServerConnection connection : connections) {
            try {
                connection.client().close();
            } catch (final Exception e) {
                LOGGER.warn(() -> "Failed to close MCP client '" + connection.label() + "'", e);
            }
        }
    }

    /**
     * A connected MCP tool server.
     *
     * @param label  The name used for the server in messages and in the resource tools.
     * @param config The server's configuration.
     * @param client The connected client.
     */
    record McpServerConnection(String label, McpToolServerConfig config, McpSyncClient client) {

        McpServerConnection {
            Objects.requireNonNull(label);
            Objects.requireNonNull(client);
        }
    }
}
