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

package stroom.security.server;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class ApiTokenCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiTokenCache.class);

    private static final int MAX_CACHE_ENTRIES = 10000;

    private final LoadingCache<String, Optional<TokenAndExpiry>> cache;

    @Inject
    @SuppressWarnings("unchecked")
    public ApiTokenCache(final CacheManager cacheManager,
                         final AuthenticationServiceClients authenticationServiceClients,
                         final JWTService jwtService) {

        final CacheLoader<String, Optional<TokenAndExpiry>> cacheLoader = CacheLoader.from(userId -> {
            final String token = authenticationServiceClients.getUsersApiToken(userId);
            if (token != null) {
                final Optional<JwtClaims> claims = jwtService.verifyToken(token);
                try {
                    if (claims.isPresent()) {
                        return Optional.of(new TokenAndExpiry(token, claims.get().getExpirationTime().getValueInMillis()));
                    }
                } catch (final MalformedClaimException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }

            return Optional.empty();
        });
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterWrite(30, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("API Token Cache", cacheBuilder, cache);
    }

    String get(final String userId) {
        Optional<TokenAndExpiry> optional = cache.getUnchecked(userId);
        if (optional.isPresent()) {
            final long oldTime = System.currentTimeMillis() - 60000;
            if (optional.get().getExpiryMs() < oldTime) {
                LOGGER.debug("Removing cached token for '{}' as it has expired", userId);
                remove(userId);
                optional = cache.getUnchecked(userId);
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
