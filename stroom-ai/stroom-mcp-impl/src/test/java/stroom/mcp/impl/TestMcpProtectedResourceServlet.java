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

import stroom.config.common.UriFactory;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpProtectedResourceServlet {

    private final OpenIdConfiguration openIdConfiguration = Mockito.mock(OpenIdConfiguration.class);
    private final UriFactory uriFactory = Mockito.mock(UriFactory.class);
    private final McpProtectedResourceServlet servlet = new McpProtectedResourceServlet(
            () -> openIdConfiguration, () -> uriFactory);

    @Test
    void anExternalIdentityProviderIsWhereAClientSignsIn() {
        Mockito.when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);
        Mockito.when(openIdConfiguration.getIssuer()).thenReturn("https://idp.example.com/realms/stroom");
        Mockito.when(uriFactory.publicUri("/mcp")).thenReturn(URI.create("https://stroom.example.com/mcp"));

        final Map<String, Object> metadata = servlet.metadata();

        assertThat(metadata)
                .containsEntry("resource", "https://stroom.example.com/mcp")
                .containsEntry("authorization_servers", List.of("https://idp.example.com/realms/stroom"))
                .containsEntry("bearer_methods_supported", List.of("header"));
    }

    @Test
    void withStroomsOwnIdentityProviderThereIsNothing() {
        Mockito.when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.INTERNAL_IDP);
        Mockito.when(openIdConfiguration.getIssuer()).thenReturn("https://stroom.example.com");

        assertThat(servlet.metadata()).isNull();
    }

    @Test
    void withNoIssuerThereIsNothing() {
        Mockito.when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);

        assertThat(servlet.metadata()).isNull();
    }

    @Test
    void theMetadataIsAtBothPathsClientsTry() {
        assertThat(servlet.getPathSpecs()).containsExactlyInAnyOrder(
                "/.well-known/oauth-protected-resource",
                "/.well-known/oauth-protected-resource/mcp");
    }
}
