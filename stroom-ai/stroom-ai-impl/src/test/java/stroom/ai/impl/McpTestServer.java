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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/// An MCP server running in-process on a random port, for testing the client side against the real
/// Streamable HTTP transport. Each request's `Authorization` header is recorded, and is also available to tool
/// handlers through the transport context under [#AUTHORIZATION_KEY].
final class McpTestServer implements AutoCloseable {

    static final String AUTHORIZATION_KEY = "authorization";
    private static final String MCP_PATH = "/mcp";

    private final Server jetty;
    private final McpSyncServer mcpServer;
    private final List<String> authorizationHeaders = Collections.synchronizedList(new ArrayList<>());

    private McpTestServer(final List<SyncToolSpecification> tools,
                          final List<SyncResourceSpecification> resources) throws Exception {
        final HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider
                .builder()
                .mcpEndpoint(MCP_PATH)
                .contextExtractor(request -> {
                    final String authorization = request.getHeader("Authorization");
                    authorizationHeaders.add(authorization == null
                            ? ""
                            : authorization);
                    return McpTransportContext.create(authorization == null
                            ? Map.of()
                            : Map.of(AUTHORIZATION_KEY, authorization));
                })
                .build();

        final ServerCapabilities.Builder capabilities = ServerCapabilities.builder().tools(true);
        if (!resources.isEmpty()) {
            capabilities.resources(false, false);
        }
        mcpServer = McpServer.sync(transport)
                .serverInfo("test-server", "1.0")
                .capabilities(capabilities.build())
                .tools(tools)
                .resources(resources)
                .build();

        jetty = new Server();
        final ServerConnector connector = new ServerConnector(jetty);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        jetty.addConnector(connector);
        final ServletContextHandler context = new ServletContextHandler();
        context.addServlet(new ServletHolder(transport), "/*");
        jetty.setHandler(context);
        jetty.start();
    }

    /// Starts a server offering the given tools and resources.
    static McpTestServer start(final List<SyncToolSpecification> tools,
                               final List<SyncResourceSpecification> resources) throws Exception {
        return new McpTestServer(tools, resources);
    }

    /// @return The URL of the server's MCP endpoint.
    String endpoint() {
        final int port = ((ServerConnector) jetty.getConnectors()[0]).getLocalPort();
        return "http://127.0.0.1:" + port + MCP_PATH;
    }

    /// @return The `Authorization` header of every request received so far, empty where there was none.
    List<String> authorizationHeaders() {
        synchronized (authorizationHeaders) {
            return List.copyOf(authorizationHeaders);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            mcpServer.close();
        } finally {
            jetty.stop();
        }
    }
}
