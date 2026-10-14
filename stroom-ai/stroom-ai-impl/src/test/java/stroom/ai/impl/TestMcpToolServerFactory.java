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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

    private final McpToolServerFactory factory = new McpToolServerFactory(
            () -> storedSecrets, null, null, () -> securityContext, () -> userIdentityFactory);

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
        assertThatThrownBy(() -> factory.createToolset(doc))
                .isInstanceOf(IllegalArgumentException.class);
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
}
