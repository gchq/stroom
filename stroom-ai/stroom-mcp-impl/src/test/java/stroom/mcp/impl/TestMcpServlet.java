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

import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpServlet {

    private final StroomMcpServer server = Mockito.mock(StroomMcpServer.class);
    private final HttpServletStreamableServerTransportProvider transport =
            Mockito.mock(HttpServletStreamableServerTransportProvider.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiConfig aiConfig = Mockito.mock(AiConfig.class);
    private final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    private final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    private final McpServlet servlet = new McpServlet(() -> server, securityContext, () -> aiConfig);

    @BeforeEach
    void setUp() {
        Mockito.when(server.transport()).thenReturn(transport);
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig());
        Mockito.when(securityContext.hasAppPermission(AppPermission.USE_MCP_TOOLS)).thenReturn(true);
    }

    @Test
    void aRequestIsServedByTheTransport() throws Exception {
        servlet.service(request, response);

        Mockito.verify(server).endIdleSessions(Mockito.anyLong());
        Mockito.verify(transport).service(request, response);
    }

    @Test
    void aRequestForAnOpenSessionIsServed() throws Exception {
        Mockito.when(request.getHeader("Mcp-Session-Id")).thenReturn("s1");
        Mockito.when(server.isOpen("s1")).thenReturn(true);

        servlet.service(request, response);

        Mockito.verify(transport).service(request, response);
    }

    @Test
    void aRequestForASessionNotOpenHereIsNotFound() throws Exception {
        // So the client starts a new session.
        Mockito.when(request.getHeader("Mcp-Session-Id")).thenReturn("s1");

        servlet.service(request, response);

        Mockito.verify(response).sendError(Mockito.eq(HttpServletResponse.SC_NOT_FOUND), Mockito.anyString());
        Mockito.verifyNoInteractions(transport);
    }

    @Test
    void aUserWithoutThePermissionIsRefused() throws Exception {
        Mockito.when(securityContext.hasAppPermission(AppPermission.USE_MCP_TOOLS)).thenReturn(false);

        servlet.service(request, response);

        Mockito.verify(response).sendError(Mockito.eq(HttpServletResponse.SC_FORBIDDEN),
                Mockito.contains("Use MCP Tools"));
        Mockito.verifyNoInteractions(transport);
    }

    @Test
    void whenTurnedOffThereIsNoEndpoint() throws Exception {
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig(false, null, null, null, null,
                null));

        servlet.service(request, response);

        Mockito.verify(response).sendError(Mockito.eq(HttpServletResponse.SC_NOT_FOUND), Mockito.anyString());
        Mockito.verifyNoInteractions(transport);
    }

    @Test
    void theEndpointIsAtMcp() {
        assertThat(servlet.getPathSpecs()).containsExactly("/mcp");
    }
}
