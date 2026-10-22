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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.McpEndpointConfig;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.IsServlet;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;

import io.modelcontextprotocol.spec.HttpHeaders;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

/// Stroom's MCP endpoint, `/mcp`, on the application port: MCP clients (e.g. VS Code or Claude) use Stroom's tools
/// through it, as the user signed in (by bearer token or API key, through the security filter like any other
/// servlet), under their tool policies. A user needs the Use MCP Tools permission.
///
/// The MCP Streamable HTTP transport serves the requests: POST for messages, GET for the server's stream, DELETE
/// to end a session (see [StroomMcpServer]). A session lives on the node that started it, so a load balancer must
/// send a session's requests to the same node (by its `Mcp-Session-Id` header); a request for a session this node
/// doesn't have is answered 404, which tells the client to start a new one. So is any request (a call, an answer to
/// a form, a listening stream, or a DELETE) for a session that isn't the user's: as far as they can tell, there is
/// no such session.
public class McpServlet extends HttpServlet implements IsServlet {

    /// The endpoint's path.
    static final String PATH = ResourcePaths.MCP_PATH;

    private final Provider<StroomMcpServer> serverProvider;
    private final SecurityContext securityContext;
    private final Provider<AiConfig> aiConfigProvider;

    @Inject
    McpServlet(final Provider<StroomMcpServer> serverProvider,
               final SecurityContext securityContext,
               final Provider<AiConfig> aiConfigProvider) {
        this.serverProvider = serverProvider;
        this.securityContext = securityContext;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// @return The endpoint's path.
    @Override
    public Set<String> getPathSpecs() {
        return Set.of(PATH);
    }

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final McpEndpointConfig config = NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getMcpEndpoint,
                new McpEndpointConfig());
        if (!config.isEnabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Stroom's MCP endpoint is turned off.");
        } else if (!securityContext.hasAppPermission(AppPermission.USE_MCP_TOOLS)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Using Stroom's tools over MCP needs the '"
                                                                 + AppPermission.USE_MCP_TOOLS.getDisplayValue()
                                                                 + "' permission.");
        } else {
            final StroomMcpServer server = serverProvider.get();
            final String sessionId = request.getHeader(HttpHeaders.MCP_SESSION_ID);
            if (sessionId != null && !server.isOpenFor(sessionId, securityContext.getUserRef())) {
                // As the protocol says, so the client starts a new session.
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "There is no such MCP session. Start a new one.");
            } else {
                // So the work on a request knows when the client's request has ended (see McpRequestEnd).
                server.transport().service(McpRequestEnd.watch(request), response);
            }
        }
    }
}
