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

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.config.common.UriFactory;
import stroom.security.common.impl.AuthenticationState;
import stroom.security.openid.api.Pkce;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Singleton
public class AuthenticationStateCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthenticationStateCache.class);

    private final StroomCache<String, AuthenticationState> cache;
    private final UriFactory uriFactory;

    @Inject
    public AuthenticationStateCache(final Provider<AuthenticationConfig> configProvider,
                                    final CacheManager cacheManager,
                                    final UriFactory uriFactory) {
        this.uriFactory = uriFactory;
        cache = cacheManager.create("Authentication State Cache",
                () -> configProvider.get().getAuthenticationStateCache());
    }

    /**
     * Create state for the logout flow. The redirect here is the application's public root, used as the
     * {@code post_logout_redirect_uri} the IdP returns the user to after sign out. The sign-in flow uses
     * {@link #create(String, String, boolean)}, whose redirect is the OIDC sign-in callback.
     * <p>
     * A 'state' is a single-use, cryptographically random string used to prevent replay attacks: its value
     * is included in the request to the IdP and checked when the IdP calls back.
     */
    public AuthenticationState create(final String url,
                                      final boolean prompt) {
        final String stateId = createRandomString(20);
        final String nonce = createRandomString(20);
        final String codeVerifier = Pkce.createCodeVerifier();

        // The public root is the post-logout landing page (post_logout_redirect_uri), not an authorization
        // redirect_uri.
        final String redirectUri = uriFactory.publicUri("/").toString();

        final AuthenticationState state = new AuthenticationState(
                stateId, url, redirectUri, nonce, prompt, codeVerifier);
        LOGGER.debug(() -> LogUtil.message("Creating {}", state));

        cache.put(stateId, state);
        return state;
    }

    /**
     * Create state for the SPA/BFF flow, where the OIDC redirect_uri is a dedicated callback endpoint
     * distinct from the initiating URI the user is returned to after authenticating.
     */
    public AuthenticationState create(final String initiatingUrl,
                                      final String callbackUri,
                                      final boolean prompt) {
        final String stateId = createRandomString(20);
        final String nonce = createRandomString(20);
        // A fresh PKCE code verifier per flow; its S256 challenge goes on the authorization request.
        final String codeVerifier = Pkce.createCodeVerifier();

        final AuthenticationState state = new AuthenticationState(
                stateId, initiatingUrl, callbackUri, nonce, prompt, codeVerifier);
        LOGGER.debug(() -> LogUtil.message("Creating {}", state));

        cache.put(stateId, state);
        return state;
    }

    public Optional<AuthenticationState> getAndRemove(final String stateId) {
        // Atomic get-and-remove, so two concurrent consumptions of the same state cannot both retrieve it.
        return cache.getAndRemove(stateId);
    }

    private String createRandomString(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }
}
