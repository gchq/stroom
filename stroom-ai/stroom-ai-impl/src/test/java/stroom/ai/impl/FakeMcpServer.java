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

import stroom.openai.shared.McpToolPolicyRule;
import stroom.openai.shared.McpToolServerConfig;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// An MCP tool server for tests: a mocked client that lists the tools it is given and answers every call with
/// the server's name and the tool called, recording each call.
public final class FakeMcpServer {

    private final String label;
    private final McpServerConnection connection;
    private final List<String> calls = new ArrayList<>();
    private final List<Tool> tools;

    /// @param label    The server's name.
    /// @param trusted  True if an administrator has said it is trusted.
    /// @param policies The administrator's tool policies for it.
    /// @param tools    Its tools.
    public FakeMcpServer(final String label,
                         final boolean trusted,
                         final List<McpToolPolicyRule> policies,
                         final List<Tool> tools) {
        this.label = label;
        this.tools = List.copyOf(tools);
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.listTools()).thenReturn(new ListToolsResult(tools, null));
        Mockito.when(client.callTool(Mockito.any(CallToolRequest.class))).thenAnswer(invocation -> {
            final String tool = invocation.<CallToolRequest>getArgument(0).name();
            calls.add(tool);
            return CallToolResult.builder()
                    .content(List.of(new TextContent(label + "/" + tool)))
                    .isError(false)
                    .build();
        });
        final McpToolServerConfig config = McpToolServerConfig.builder()
                .name(label)
                .endpoint("http://" + label + ".example/mcp")
                .trusted(trusted)
                .toolPolicies(policies)
                .build();
        connection = new McpServerConnection(label, config, client, null);
    }

    /// @param servers The servers, in the order they are configured.
    /// @return A toolset over them.
    public static McpToolset toolset(final FakeMcpServer... servers) {
        final List<McpServerConnection> connections = new ArrayList<>();
        for (final FakeMcpServer server : servers) {
            connections.add(server.connection);
        }
        return new McpToolset(connections, List.of(), false);
    }

    /// @param name        The tool's name.
    /// @param readOnly    Its read-only hint, or null for none.
    /// @param destructive Its destructive hint, or null for none.
    /// @param openWorld   Its open-world hint, or null for none.
    /// @param confirms    True if it says (in `_meta`) that it asks the user itself, through a form.
    /// @return The tool, as its server lists it.
    public static Tool tool(final String name,
                            final Boolean readOnly,
                            final Boolean destructive,
                            final Boolean openWorld,
                            final boolean confirms) {
        final Tool.Builder builder = Tool.builder()
                .name(name)
                .description("The " + name + " tool.")
                .inputSchema(Map.of("type", "object"));
        if (readOnly != null || destructive != null || openWorld != null) {
            builder.annotations(new ToolAnnotations(null, readOnly, destructive, null, openWorld, null));
        }
        if (confirms) {
            builder.meta(Map.of(McpToolset.META_CONFIRMS, true));
        }
        return builder.build();
    }

    /// @param name The tool's name.
    /// @return A tool with no annotations.
    public static Tool tool(final String name) {
        return tool(name, null, null, null, false);
    }

    /// @return The server's name.
    public String label() {
        return label;
    }

    /// @return The names of the server's tools, as it lists them.
    public List<String> listedNames() {
        return tools.stream()
                .map(Tool::name)
                .toList();
    }

    /// @return The tools called on this server, by the names the server gave them, in order.
    public List<String> calls() {
        return calls;
    }
}
