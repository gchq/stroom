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

package stroom.security.identity.openid;

import stroom.config.common.UriFactory;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TestOpenIdDiscovery {

    private static final String PUBLIC_ROOT = "https://stroom.example.com";

    private OpenIdConfigurationResponse discovery() {
        final UriFactory uriFactory = Mockito.mock(UriFactory.class);
        when(uriFactory.publicUri(anyString()))
                .thenAnswer(invocation -> URI.create(PUBLIC_ROOT + invocation.getArgument(0)));

        // Only the uri factory is used to build the discovery document.
        final OpenIdResourceImpl resource = new OpenIdResourceImpl(
                null, null, () -> uriFactory, null, null);
        return JsonUtil.readValue(resource.openIdConfiguration(), OpenIdConfigurationResponse.class);
    }

    @Test
    void advertisesOnlyTheAuthorizationCodeFlow() {
        // The provider only implements the code flow, so it must not claim implicit or hybrid support.
        assertThat(discovery().getResponseTypesSupported()).containsExactly("code");
    }

    @Test
    void advertisesTheGrantTypesItActuallySupports() {
        assertThat(discovery().getGrantTypesSupported())
                .containsExactlyInAnyOrder("authorization_code", "refresh_token");
    }

    @Test
    void advertisesS256PkceSupport() {
        assertThat(discovery().getCodeChallengeMethodsSupported()).containsExactly("S256");
    }

    @Test
    void advertisesHowTheClientAuthenticates() {
        assertThat(discovery().getTokenEndpointAuthMethodsSupported())
                .containsExactly("client_secret_post");
    }

    @Test
    void advertisesTheCoreEndpointsAndSigningAlg() {
        final OpenIdConfigurationResponse discovery = discovery();
        // The issuer is an https URL, not a bare word, so a conformant client accepts it.
        assertThat(discovery.getIssuer()).isEqualTo(PUBLIC_ROOT + "/oauth2/v1");
        assertThat(discovery.getAuthorizationEndpoint()).isEqualTo(PUBLIC_ROOT + "/oauth2/v1/auth");
        assertThat(discovery.getTokenEndpoint()).isEqualTo(PUBLIC_ROOT + "/oauth2/v1/token");
        assertThat(discovery.getJwksUri()).isEqualTo(PUBLIC_ROOT + "/oauth2/v1/certs");
        assertThat(discovery.getIdTokenSigningSlgValuesSupported()).containsExactly("RS256");
        assertThat(discovery.getScopesSupported()).containsExactlyInAnyOrder("openid", "email");
    }

    @Test
    void doesNotAdvertiseEndpointsItDoesNotExpose() {
        // There is no userinfo or logout endpoint on the internal provider, so a client must not be told
        // there is one.
        final OpenIdConfigurationResponse discovery = discovery();
        assertThat(discovery.getUserinfoEndpoint()).isNull();
        assertThat(discovery.getLogoutEndpoint()).isNull();
    }
}
