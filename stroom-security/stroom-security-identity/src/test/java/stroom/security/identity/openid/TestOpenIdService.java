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
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.Pkce;
import stroom.util.shared.ResourcePaths;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TestOpenIdService {

    private static final String PUBLIC_ROOT = "https://stroom.example.com";
    // The single redirect_uri the internal IdP accepts: the OIDC sign-in callback (BFF endpoint).
    private static final String SIGN_IN_OIDC_CALLBACK =
            PUBLIC_ROOT + ResourcePaths.buildSignInOidcCallbackPath();

    private OpenIdService service() {
        final UriFactory uriFactory = Mockito.mock(UriFactory.class);
        when(uriFactory.publicUri(ResourcePaths.buildSignInOidcCallbackPath()))
                .thenReturn(URI.create(SIGN_IN_OIDC_CALLBACK));
        // Only uriFactory is used by isRedirectUriAllowed; the rest are irrelevant here.
        return new OpenIdService(null, null, null, null, null, null, uriFactory);
    }

    @Test
    void onlyTheExactSignInOidcCallbackIsAllowed() {
        final OpenIdService service = service();
        assertThat(service.isRedirectUriAllowed(SIGN_IN_OIDC_CALLBACK)).isTrue();
    }

    @Test
    void pkceIsRequiredAndOnlyS256IsAccepted() {
        final OpenIdService service = service();

        // A well-formed S256 challenge is accepted.
        assertThat(service.isValidS256Pkce("a-challenge", OpenId.CODE_CHALLENGE_METHOD__S256)).isTrue();

        // PKCE is mandatory, so a missing or blank challenge is refused.
        assertThat(service.isValidS256Pkce(null, OpenId.CODE_CHALLENGE_METHOD__S256)).isFalse();
        assertThat(service.isValidS256Pkce("  ", OpenId.CODE_CHALLENGE_METHOD__S256)).isFalse();

        // Only S256 is accepted; the weaker 'plain' method and a missing method are refused.
        assertThat(service.isValidS256Pkce("a-challenge", "plain")).isFalse();
        assertThat(service.isValidS256Pkce("a-challenge", null)).isFalse();
    }

    @Test
    void theCodeVerifierMustHashToTheStoredChallenge() {
        final OpenIdService service = service();
        final String verifier = Pkce.createCodeVerifier();
        final String challenge = Pkce.createS256Challenge(verifier);

        assertThat(service.isCodeVerifierValid(verifier, challenge)).isTrue();

        // A different verifier, or a missing one, does not match.
        assertThat(service.isCodeVerifierValid(Pkce.createCodeVerifier(), challenge)).isFalse();
        assertThat(service.isCodeVerifierValid(null, challenge)).isFalse();
        assertThat(service.isCodeVerifierValid(verifier, null)).isFalse();
    }

    @Test
    void anythingButTheExactSignInOidcCallbackIsRejected() {
        final OpenIdService service = service();

        // The bare public root is no longer the redirect_uri (it is only the post-logout landing), so it
        // is rejected here.
        assertThat(service.isRedirectUriAllowed(PUBLIC_ROOT)).isFalse();
        // A different host, and the classic suffix-confusion host.
        assertThat(service.isRedirectUriAllowed("https://evil.example.com")).isFalse();
        assertThat(service.isRedirectUriAllowed(
                "https://stroom.example.com.evil.com/api/auth/flow/v1/signin-oidc")).isFalse();
        // The right path on the wrong host.
        assertThat(service.isRedirectUriAllowed(
                "https://evil.example.com/api/auth/flow/v1/signin-oidc")).isFalse();
        // The right host but a different path.
        assertThat(service.isRedirectUriAllowed("https://stroom.example.com/dashboard")).isFalse();
        // Scheme downgrade of the exact callback.
        assertThat(service.isRedirectUriAllowed(
                "http://stroom.example.com/api/auth/flow/v1/signin-oidc")).isFalse();
        assertThat(service.isRedirectUriAllowed(null)).isFalse();
    }

    @Test
    void wildcardsAreTreatedAsLiteralText_notPatterns() {
        // The whole point of the gold-standard change: there is no regex in the redirect check, so a
        // value containing regex metacharacters matches nothing but itself.
        final OpenIdService service = service();
        assertThat(service.isRedirectUriAllowed(".*")).isFalse();
        assertThat(service.isRedirectUriAllowed("https://stroom.example.com.*")).isFalse();
        assertThat(service.isRedirectUriAllowed("https://.*")).isFalse();
    }
}
