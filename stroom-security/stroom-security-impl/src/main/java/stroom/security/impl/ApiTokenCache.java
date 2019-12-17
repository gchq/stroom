/*
 * Copyright 2016 Crown Copyright
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

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class ApiTokenCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiTokenCache.class);
    private static final String CACHE_NAME = "API Token Cache";

    private final AuthenticationServiceClients authenticationServiceClients;
    private final JWTService jwtService;
    private final ICache<String, Optional<TokenAndExpiry>> cache;

    @Inject
    public ApiTokenCache(final CacheManager cacheManager,
                         final AuthenticationConfig authenticationConfig,
                         final AuthenticationServiceClients authenticationServiceClients,
                         final JWTService jwtService) {
        this.authenticationServiceClients = authenticationServiceClients;
        this.jwtService = jwtService;
        cache = cacheManager.create(CACHE_NAME, authenticationConfig::getApiTokenCache, this::create);
    }

    private Optional<TokenAndExpiry> create(final String userId) {
        String token = authenticationServiceClients.getUsersApiToken(userId);
        if (token != null) {
            try {
                token = jwtService.refreshTokenIfExpired(token);
                final JwtClaims claims = jwtService.verifyToken(token);
                return Optional.of(new TokenAndExpiry(token, claims.getExpirationTime().getValueInMillis()));
            } catch (final MalformedClaimException | InvalidJwtException e) {
                LOGGER.warn(e.getMessage());
            }
        }

        return Optional.empty();
    }

    String get(final String userId) {
        Optional<TokenAndExpiry> optional = cache.get(userId);
        if (optional.isPresent()) {
            final long oldTime = System.currentTimeMillis() - 60000;
            if (optional.get().getExpiryMs() < oldTime) {
                LOGGER.debug("Removing cached token for '{}' as it has expired", userId);
                remove(userId);
                optional = cache.get(userId);
            }
        } else {
            remove(userId);
        }

        return optional.map(TokenAndExpiry::getToken).orElse(null);
    }

    void remove(final String userId) {
        cache.invalidate(userId);
    }

    private static class TokenAndExpiry {
        private final String token;
        private final long expiryMs;

        TokenAndExpiry(final String token, final long expiryMs) {
            this.token = token;
            this.expiryMs = expiryMs;
        }

        String getToken() {
            return token;
        }

        long getExpiryMs() {
            return expiryMs;
        }
    }
}
