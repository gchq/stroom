/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.credentials.api.KeyStore;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.openai.shared.McpAuthMode;
import stroom.openai.shared.McpToolServerConfig;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpHeader;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMcpToolServerFactory {

    private final StoredSecrets storedSecrets = new StoredSecrets() {
        @Override
        public StoredSecret get(final String name) {
            return switch (name) {
                case "token" -> new StoredSecret(null, new AccessTokenSecret("secret-token"), null);
                case "userpass" -> new StoredSecret(null, new UsernamePasswordSecret("user", "pass"), null);
                default -> null;
            };
        }

        @Override
        public KeyStore getKeyStore(final String name) {
            return null;
        }
    };

    private final UserIdentity userIdentity = Mockito.mock(UserIdentity.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final UserIdentityFactory userIdentityFactory = Mockito.mock(UserIdentityFactory.class);
    private final OpenIdConfiguration openIdConfiguration = Mockito.mock(OpenIdConfiguration.class);

    {
        Mockito.when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);
    }

    private final McpToolServerFactory factory = new McpToolServerFactory(
            () -> storedSecrets, null, null, () -> securityContext, () -> userIdentityFactory, new McpClientCache(),
            () -> null, AiConfig::new, () -> openIdConfiguration);

    @Test
    void noneMode() {
        assertThat(headers(server(McpAuthMode.NONE, null))).isEmpty();
    }

    @Test
    void bearerToken() {
        assertThat(headers(server(McpAuthMode.BEARER_TOKEN, "token")))
                .containsEntry("Authorization", "Bearer secret-token");
    }

    @Test
    void apiKeyHeaderWithPrefix() {
        final McpToolServerConfig server = McpToolServerConfig.builder()
                .name("s")
                .endpoint("https://x/mcp")
                .authMode(McpAuthMode.API_KEY)
                .apiKeyHeaderName("X-Api-Key")
                .apiKeyValuePrefix("key ")
                .credentialName("token")
                .build();
        assertThat(headers(server)).containsEntry("X-Api-Key", "key secret-token");
    }

    @Test
    void basicAuth() {
        assertThat(headers(server(McpAuthMode.BASIC, "userpass")))
                .containsEntry("Authorization", "Basic dXNlcjpwYXNz"); // base64("user:pass")
    }

    @Test
    void missingCredentialIsRejected() {
        assertThatThrownBy(() -> headers(server(McpAuthMode.BEARER_TOKEN, "absent")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot be found");
    }

    @Test
    void stroomSessionTokenPassThrough() {
        Mockito.when(securityContext.getUserIdentity()).thenReturn(userIdentity);
        Mockito.when(userIdentityFactory.getAuthHeaders(userIdentity))
                .thenReturn(Map.of("Authorization", "Bearer session-jwt"));
        assertThat(headers(server(McpAuthMode.STROOM_SESSION_TOKEN, null)))
                .containsEntry("Authorization", "Bearer session-jwt");
    }

    @Test
    void stroomSessionTokenWithNoTokenIsRejected() {
        Mockito.when(securityContext.getUserIdentity()).thenReturn(userIdentity);
        Mockito.when(userIdentityFactory.getAuthHeaders(userIdentity)).thenReturn(Map.of());
        assertThatThrownBy(() -> headers(server(McpAuthMode.STROOM_SESSION_TOKEN, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no OAuth2 token");
    }

    @Test
    void emptyToolsetWhenNoServers() {
        final McpToolset toolset = factory.createToolset(
                OpenAIModelDoc.builder().uuid("u").name("m").build());
        assertThat(toolset.isEmpty()).isTrue();
    }

    @Test
    void metadataEndpointIsRejected() {
        final OpenAIModelDoc doc = OpenAIModelDoc.builder()
                .uuid("u")
                .name("m")
                .mcpToolServers(java.util.List.of(McpToolServerConfig.builder()
                        .name("ssrf")
                        .endpoint("http://169.254.169.254/latest/meta-data/")
                        .build()))
                .build();
        try (final McpToolset toolset = factory.createToolset(doc)) {
            assertThat(toolset.isEmpty()).isTrue();
            assertThat(toolset.getWarnings())
                    .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                    .contains("'ssrf' is not allowed");
        }
    }

    @Test
    void stroomSessionToken_needsAnExternalIdentityProvider() {
        Mockito.when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.INTERNAL_IDP);

        assertThatThrownBy(() -> headers(server(McpAuthMode.STROOM_SESSION_TOKEN, null)))
                .hasMessageContaining("are unavailable")
                .hasMessageContaining("external identity provider");
        // Other modes don't depend on it.
        assertThat(headers(server(McpAuthMode.NONE, null))).isEmpty();
    }

    private Map<String, String> headers(final McpToolServerConfig server) {
        return factory.buildAuthHeaders(server, server.getName());
    }

    private McpToolServerConfig server(final McpAuthMode authMode, final String credentialName) {
        return McpToolServerConfig.builder()
                .name("s")
                .endpoint("https://x/mcp")
                .authMode(authMode)
                .credentialName(credentialName)
                .build();
    }

    @Test
    void toolsAreListedAndCalledOverHttp() throws Exception {
        try (final McpTestServer server = McpTestServer.start(List.of(echoTool()), List.of())) {
            final OpenAIModelDoc doc = modelDoc(server.endpoint(), McpAuthMode.BEARER_TOKEN, "token");

            try (final McpToolset toolset = factory.createToolset(doc)) {
                final Map<ToolSpecification, ToolExecutor> tools = toolset.tools();
                // Tool names are prefixed with their server's name.
                assertThat(tools.keySet())
                        .extracting(ToolSpecification::name)
                        .containsExactly("s__echo");

                final String result = execute(tools, "s__echo", "{\"text\":\"hello\"}");
                assertThat(result).isEqualTo("hello (auth: Bearer secret-token)");
            }
            // Every request carried the credential.
            assertThat(server.authorizationHeaders())
                    .isNotEmpty()
                    .allMatch("Bearer secret-token"::equals);
        }
    }

    @Test
    void configuredHeadersAreSentButAuthenticationWins() throws Exception {
        try (final McpTestServer server = McpTestServer.start(List.of(echoTool()), List.of())) {
            final McpToolServerConfig serverConfig = serverConfig(
                    "s", server.endpoint(), McpAuthMode.BEARER_TOKEN, "token")
                    .copy()
                    .httpClientConfiguration(HttpClientConfig.builder()
                            .userAgent("Stroom-Test")
                            .headers(List.of(
                                    new HttpHeader("X-Tenant", "acme"),
                                    new HttpHeader("Authorization", "Bearer configured"),
                                    // The JDK client won't set this, which must not stop every request.
                                    new HttpHeader("Host", "elsewhere")))
                            .build())
                    .build();
            final OpenAIModelDoc doc = OpenAIModelDoc.builder()
                    .uuid("u")
                    .name("m")
                    .mcpToolServers(List.of(serverConfig))
                    .build();

            try (final McpToolset toolset = factory.createToolset(doc)) {
                assertThat(execute(toolset.tools(), "s__echo", "{\"text\":\"hi\"}"))
                        .isEqualTo("hi (auth: Bearer secret-token)");
            }
            assertThat(server.requestHeaders())
                    .isNotEmpty()
                    .allSatisfy(headers -> assertThat(headers)
                            .containsEntry("x-tenant", "acme")
                            .containsEntry("user-agent", "Stroom-Test")
                            .containsEntry("authorization", "Bearer secret-token"));
        }
    }

    @Test
    void sessionTokenIsFetchedForEachRequest() throws Exception {
        Mockito.when(securityContext.getUserIdentity()).thenReturn(userIdentity);
        // Each time Stroom is asked for the user's token it returns a newer one, as it would after a refresh.
        final int[] calls = {0};
        Mockito.when(userIdentityFactory.getAuthHeaders(userIdentity))
                .thenAnswer(invocation -> Map.of("Authorization", "Bearer jwt-" + (++calls[0])));

        try (final McpTestServer server = McpTestServer.start(List.of(echoTool()), List.of())) {
            final OpenAIModelDoc doc = modelDoc(server.endpoint(), McpAuthMode.STROOM_SESSION_TOKEN, null);

            try (final McpToolset toolset = factory.createToolset(doc)) {
                // Requests are sent from the HTTP client's threads, where there is no security context, so
                // the identity must have been captured when the toolset was built.
                Mockito.when(securityContext.getUserIdentity()).thenReturn(null);
                execute(toolset.tools(), "s__echo", "{\"text\":\"hi\"}");
            }
            assertThat(server.authorizationHeaders())
                    .contains("Bearer jwt-2")
                    .hasSizeGreaterThan(2)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void resourcesAreOfferedAsTools() throws Exception {
        final Resource resource = Resource.builder()
                .uri("stroom://guide/xslt")
                .name("xslt")
                .description("How to write XSLT")
                .build();
        final SyncResourceSpecification resourceSpec = new SyncResourceSpecification(resource,
                (exchange, request) -> new ReadResourceResult(List.of(
                        new TextResourceContents(request.uri(), "text/markdown", "# XSLT guide"))));

        try (final McpTestServer server = McpTestServer.start(List.of(echoTool()), List.of(resourceSpec))) {
            final OpenAIModelDoc doc = modelDoc(server.endpoint(), McpAuthMode.NONE, null);

            try (final McpToolset toolset = factory.createToolset(doc)) {
                final Map<ToolSpecification, ToolExecutor> tools = toolset.tools();
                assertThat(tools.keySet())
                        .extracting(ToolSpecification::name)
                        .containsExactly("s__echo", McpToolset.LIST_RESOURCES_TOOL, McpToolset.GET_RESOURCE_TOOL);

                assertThat(execute(tools, McpToolset.LIST_RESOURCES_TOOL, "{}"))
                        .contains("stroom://guide/xslt")
                        .contains("How to write XSLT");
                assertThat(execute(tools, McpToolset.GET_RESOURCE_TOOL,
                        "{\"mcpServer\":\"s\",\"uri\":\"stroom://guide/xslt\"}"))
                        .isEqualTo("# XSLT guide");
            }
        }
    }

    @Test
    void unreachableServerIsLeftOutAndReported() throws Exception {
        try (final McpTestServer server = McpTestServer.start(List.of(echoTool()), List.of())) {
            final OpenAIModelDoc doc = OpenAIModelDoc.builder()
                    .uuid("u")
                    .name("m")
                    .mcpToolServers(List.of(
                            serverConfig("down", "http://127.0.0.1:1/mcp", McpAuthMode.NONE, null),
                            serverConfig("up", server.endpoint(), McpAuthMode.NONE, null)))
                    .build();

            try (final McpToolset toolset = factory.createToolset(doc)) {
                // The server that is up is still used.
                assertThat(toolset.tools().keySet())
                        .extracting(ToolSpecification::name)
                        .containsExactly("up__echo");
                assertThat(toolset.getWarnings())
                        .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                        .contains("'down' could not be reached");
            }
        }
    }

    @Test
    void allowedAndDeniedToolsAreApplied() throws Exception {
        final Tool other = Tool.builder().name("delete_all").inputSchema(Map.of()).build();
        final SyncToolSpecification otherSpec = new SyncToolSpecification(other,
                (exchange, request) -> CallToolResult.builder().addTextContent("deleted").build());

        try (final McpTestServer server = McpTestServer.start(List.of(echoTool(), otherSpec), List.of())) {
            final OpenAIModelDoc doc = OpenAIModelDoc.builder()
                    .uuid("u")
                    .name("m")
                    .mcpToolServers(List.of(serverConfig("s", server.endpoint(), McpAuthMode.NONE, null)
                            .copy()
                            .deniedTools(List.of("delete_*"))
                            .build()))
                    .build();

            try (final McpToolset toolset = factory.createToolset(doc)) {
                assertThat(toolset.tools().keySet())
                        .extracting(ToolSpecification::name)
                        .containsExactly("s__echo");
            }
        }
    }

    @Test
    void connectionsAreReusedWithinAChat() throws Exception {
        try (final McpTestServer server = McpTestServer.start(List.of(echoTool()), List.of())) {
            final OpenAIModelDoc doc = modelDoc(server.endpoint(), McpAuthMode.NONE, null);

            final McpToolset first = factory.createToolset(doc, 1);
            first.tools();
            first.close();
            final int requestsAfterFirst = server.authorizationHeaders().size();

            final McpToolset second = factory.createToolset(doc, 1);
            second.tools();
            second.close();

            // The same connection is used, and its tool list is cached, so no more requests were made.
            assertThat(second.getConnections().getFirst()).isSameAs(first.getConnections().getFirst());
            assertThat(server.authorizationHeaders()).hasSize(requestsAfterFirst);
        }
    }

    private static SyncToolSpecification echoTool() {
        final Tool tool = Tool.builder()
                .name("echo")
                .description("Echoes the text back")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("text", Map.of("type", "string"))))
                .build();
        return new SyncToolSpecification(tool, (exchange, request) -> CallToolResult.builder()
                .addTextContent(request.arguments().get("text")
                                + " (auth: " + exchange.transportContext().get(McpTestServer.AUTHORIZATION_KEY) + ")")
                .isError(false)
                .build());
    }

    private static String execute(final Map<ToolSpecification, ToolExecutor> tools,
                                  final String name,
                                  final String arguments) {
        final ToolExecutor executor = tools.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
        return executor.execute(ToolExecutionRequest.builder().id("1").name(name).arguments(arguments).build(), 1);
    }

    private static OpenAIModelDoc modelDoc(final String endpoint,
                                           final McpAuthMode authMode,
                                           final String credentialName) {
        return OpenAIModelDoc.builder()
                .uuid("u")
                .name("m")
                .mcpToolServers(List.of(serverConfig("s", endpoint, authMode, credentialName)))
                .build();
    }

    private static McpToolServerConfig serverConfig(final String name,
                                                    final String endpoint,
                                                    final McpAuthMode authMode,
                                                    final String credentialName) {
        return McpToolServerConfig.builder()
                .enabled(true)
                .name(name)
                .endpoint(endpoint)
                .authMode(authMode)
                .credentialName(credentialName)
                .build();
    }
}
