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

package stroom.security.common.impl;

import stroom.security.openid.api.OpenId;

import jakarta.ws.rs.core.UriBuilder;

import java.util.Set;

public class AuthenticationState {

    private static final Set<String> RESERVED_PARAMS = Set.of(
            OpenId.AUTH_USER,
            OpenId.CLIENT_ID,
            OpenId.CLIENT_SECRET,
            OpenId.CODE,
            OpenId.GRANT_TYPE,
            OpenId.NONCE,
            OpenId.PROMPT,
            OpenId.REDIRECT_URI,
            OpenId.RESPONSE_TYPE,
            OpenId.SCOPE,
            OpenId.STATE
    );

    private final String id;
    private final String url;
    private final String initiatingUri;
    private final String redirectUri;
    private final String nonce;
    private final boolean prompt;

    public AuthenticationState(final String id,
                               final String url,
                               final String nonce,
                               final boolean prompt) {
        this.id = id;
        this.url = url;
        this.nonce = nonce;
        this.prompt = prompt;

        // Make sure the initiating URI doesn't contain any reserved OIDC params.
        this.initiatingUri = createInitiatingUri(url);
        // Create a simple redirect URI.
        this.redirectUri = createRedirectUri(url);
    }

    /**
     * The id of this state.
     *
     * @return The id of this state.
     */
    public String getId() {
        return id;
    }

    /**
     * The URI of the initiating request that this state is linked to.
     *
     * @return The URL of the initiating request that this state is linked to.
     */
    public String getInitiatingUri() {
        return initiatingUri;
    }

    /**
     * The URI that should be sent to the IDP that the IDP will use to redirect back one authenticated.
     *
     * @return The URI that should be sent to the IDP that the IDP will use to redirect back one authenticated.
     */
    public String getRedirectUri() {
        return redirectUri;
    }

    /**
     * A 'nonce' is a single use, cryptographically random string,
     * and it's use here is to validate the authenticity of a token.
     * <p>
     * A nonce is used in the authentication flow - the hash is included in the original AuthenticationRequest
     * that Stroom makes to the Authentication Service. When Stroom subsequently receives an access code
     * it retrieves the ID token from the Authentication Service and expects to see
     * the hash of the nonce on the token. It can then compare the hashes.
     *
     * @return The nonce string.
     */
    public String getNonce() {
        return nonce;
    }

    /**
     * Determine if the next auth call should force a prompt.
     *
     * @return True if the next auth call should force a prompt.
     */
    public boolean isPrompt() {
        return prompt;
    }

    @Override
    public String toString() {
        return "AuthenticationState{" +
                "id='" + id + '\'' +
                ", url='" + url + '\'' +
                ", initiatingUri='" + initiatingUri + '\'' +
                ", redirectUri='" + redirectUri + '\'' +
                ", nonce='" + nonce + '\'' +
                ", prompt=" + prompt +
                '}';
    }

    private static String createInitiatingUri(final String url) {
        final UriBuilder uriBuilder = UriBuilder.fromUri(url);

        // When the auth service has performed authentication it will redirect
        // back to the current URL with some additional parameters (e.g.
        // `state` and `accessCode`). It is important that these parameters are
        // not provided by our redirect URL else the redirect URL that the
        // authentication service redirects back to may end up with multiple
        // copies of these parameters which will confuse Stroom as it will not
        // know which one of the param values to use (i.e. which were on the
        // original redirect request and which have been added by the
        // authentication service). For this reason we will cleanse the URL of
        // any reserved parameters here. The authentication service should do
        // the same to the redirect URL before adding its additional
        // parameters.
        RESERVED_PARAMS.forEach(param -> uriBuilder.replaceQueryParam(param, new Object[0]));

        return uriBuilder.build().toString();
    }

    private static String createRedirectUri(final String url) {
        final UriBuilder uriBuilder = UriBuilder.fromUri(url);
        uriBuilder.replaceQuery("");
        return uriBuilder.build().toString();
    }
}
