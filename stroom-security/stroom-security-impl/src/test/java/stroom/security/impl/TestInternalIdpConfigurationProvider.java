/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.security.openid.api.OpenIdClient;
import stroom.security.openid.api.OpenIdClientFactory;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

class TestInternalIdpConfigurationProvider {

    @Test
    void theAudienceIsTheClientIdAndIsRequired() {
        // The internal provider mints tokens with aud = its own client id and, being a resource server,
        // requires that audience on any bearer, so a relying party verifies its tokens exactly as it
        // would a Keycloak token whose audience mapper names this client.
        final OpenIdClientFactory clientFactory = Mockito.mock(OpenIdClientFactory.class);
        Mockito.when(clientFactory.getClient())
                .thenReturn(new OpenIdClient("Stroom", "the-client-id", "secret"));

        final StroomOpenIdConfig localConfig = Mockito.mock(StroomOpenIdConfig.class);
        Mockito.when(localConfig.getAllowedAudiences()).thenReturn(Collections.emptySet());

        final InternalIdpConfigurationProvider provider = new InternalIdpConfigurationProvider(
                null, () -> localConfig, clientFactory);

        assertThat(provider.getAllowedAudiences()).containsExactly("the-client-id");
        assertThat(provider.isAudienceClaimRequired()).isTrue();
    }

    @Test
    void theIssuerIsThePublicOAuthBaseUrlNotABareWord() {
        // Token iss and the relying party's issuer validation both derive from this, so it must be the
        // https URL a conformant client expects, like Keycloak/Cognito/Google, not "stroom".
        final UriFactory uriFactory = Mockito.mock(UriFactory.class);
        Mockito.when(uriFactory.publicUri(anyString()))
                .thenAnswer(invocation -> URI.create("https://stroom.example.com" + invocation.getArgument(0)));
        Mockito.when(uriFactory.nodeUri(anyString()))
                .thenAnswer(invocation -> URI.create("https://stroom.example.com" + invocation.getArgument(0)));

        final StroomOpenIdConfig localConfig = Mockito.mock(StroomOpenIdConfig.class);
        final OpenIdClientFactory clientFactory = Mockito.mock(OpenIdClientFactory.class);

        final InternalIdpConfigurationProvider provider = new InternalIdpConfigurationProvider(
                uriFactory, () -> localConfig, clientFactory);

        assertThat(provider.getIssuer()).isEqualTo("https://stroom.example.com/oauth2/v1");
    }
}
