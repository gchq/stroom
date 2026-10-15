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

import stroom.credentials.api.StoredSecrets;
import stroom.openai.shared.McpAuthMode;
import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolPolicyRule;
import stroom.openai.shared.McpToolServerConfig;
import stroom.openai.shared.OpenAIModelTestResponse;
import stroom.openai.shared.TestMcpToolServerRequest;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentityFactory;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestOpenAIModelResourceImpl {

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final McpToolServerFactory factory = new McpToolServerFactory(
            () -> Mockito.mock(StoredSecrets.class), null, null, () -> securityContext,
            () -> Mockito.mock(UserIdentityFactory.class), new McpClientCache(), () -> null, AiConfig::new,
            () -> Mockito.mock(OpenIdConfiguration.class), () -> null);
    private final OpenAIModelResourceImpl resource = new OpenAIModelResourceImpl(
            () -> null, () -> null, () -> null, () -> securityContext, () -> factory);

    @Test
    void testMcpToolServer_listsTheToolsItWouldOffer() throws Exception {
        allowEdit(true);
        try (final McpTestServer server = McpTestServer.start(List.of(tool("find_feeds"), tool("delete_feed")),
                List.of())) {
            final McpToolServerConfig config = serverConfig(server.endpoint())
                    .copy()
                    // Disabled servers are tested too, and policies apply.
                    .enabled(false)
                    .toolPolicies(List.of(new McpToolPolicyRule("delete_.*", McpToolPolicy.DENY)))
                    .build();

            final OpenAIModelTestResponse response = resource.testMcpToolServer(
                    new TestMcpToolServerRequest("model-uuid", config));

            assertThat(response.isOk()).isTrue();
            assertThat(response.getMessage())
                    .isEqualTo("Connected. 1 tool is offered:\nfind_feeds");
        }
    }

    @Test
    void testMcpToolServer_reportsAServerThatCannotBeReached() {
        allowEdit(true);

        final OpenAIModelTestResponse response = resource.testMcpToolServer(
                new TestMcpToolServerRequest("model-uuid", serverConfig("http://127.0.0.1:1/mcp")));

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("could not be reached");
    }

    @Test
    void testMcpToolServer_needsEditPermission() {
        // Only someone who could save the settings may make Stroom connect with them.
        allowEdit(false);

        assertThatThrownBy(() -> resource.testMcpToolServer(
                new TestMcpToolServerRequest("model-uuid", serverConfig("http://127.0.0.1:1/mcp"))))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void testMcpToolServer_needsAnEndpoint() {
        allowEdit(true);

        final OpenAIModelTestResponse response = resource.testMcpToolServer(
                new TestMcpToolServerRequest("model-uuid", serverConfig(" ")));

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("endpoint");
    }

    private void allowEdit(final boolean allowed) {
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.EDIT)))
                .thenReturn(allowed);
    }

    private static McpToolServerConfig serverConfig(final String endpoint) {
        return McpToolServerConfig.builder()
                .name("s")
                .endpoint(endpoint)
                .authMode(McpAuthMode.NONE)
                .build();
    }

    private static SyncToolSpecification tool(final String name) {
        return new SyncToolSpecification(
                Tool.builder()
                        .name(name)
                        .description(name)
                        .inputSchema(Map.of("type", "object"))
                        .build(),
                (exchange, request) -> CallToolResult.builder().addTextContent("ok").build());
    }
}
