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

package stroom.security.impl;

import stroom.security.common.impl.AuthenticationState;
import stroom.security.openid.api.OpenIdConfiguration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestOpenIdManager {

    private static final String AUTH_ENDPOINT = "https://idp.example.com/auth";

    @Test
    void extraParamsAreAppendedToTheAuthUri() {
        // Google issues no refresh token without access_type=offline; the extras must reach the
        // authentication request.
        final String uri = createAuthUri(Map.of(
                "access_type", "offline",
                "prompt", "consent"), false);

        assertThat(uri)
                .contains("access_type=offline")
                .contains("prompt=consent");
    }

    @Test
    void reservedParamsCannotBeOverridden() {
        final String uri = createAuthUri(Map.of(
                "state", "attacker-chosen",
                "redirect_uri", "https://evil.example.com",
                "nonce", "fixed"), false);

        assertThat(uri)
                .doesNotContain("attacker-chosen")
                .doesNotContain("evil.example.com")
                .doesNotContain("nonce=fixed")
                // Stroom's own values are still present.
                .contains("state=the-state-id")
                .contains("nonce=the-nonce");
    }

    @Test
    void configuredPromptDefersToAForcedLogin() {
        // A forced login (prompt=login) must not be watered down by a configured prompt=consent.
        final String uri = createAuthUri(Map.of("prompt", "consent"), true);

        assertThat(uri)
                .contains("prompt=login")
                .doesNotContain("prompt=consent");
    }

    @Test
    void noExtrasLeavesTheUriUnchanged() {
        final String uri = createAuthUri(Map.of(), false);

        assertThat(uri)
                .startsWith(AUTH_ENDPOINT)
                .contains("response_type=code")
                .contains("client_id=the-client")
                .contains("code_challenge=");
    }

    private String createAuthUri(final Map<String, String> extraParams, final boolean prompt) {
        final OpenIdConfiguration openIdConfiguration = mock(OpenIdConfiguration.class);
        when(openIdConfiguration.getAuthenticationRequestExtraParams()).thenReturn(extraParams);
        lenient().when(openIdConfiguration.getRequestScopes()).thenReturn(java.util.List.of("openid", "email"));

        final AuthenticationState state = mock(AuthenticationState.class);
        when(state.getId()).thenReturn("the-state-id");
        when(state.getNonce()).thenReturn("the-nonce");
        when(state.getRedirectUri()).thenReturn("https://stroom.example.com/api/auth/flow/v1/signin-oidc");
        when(state.getCodeVerifier()).thenReturn("a-code-verifier-of-sufficient-length-1234567890");
        when(state.isPrompt()).thenReturn(prompt);

        final OpenIdManager openIdManager = new OpenIdManager(
                openIdConfiguration,
                mock(StroomUserIdentityFactory.class),
                mock(AuthenticationStateCache.class));

        return openIdManager.createAuthUri(AUTH_ENDPOINT, "the-client", state);
    }
}
