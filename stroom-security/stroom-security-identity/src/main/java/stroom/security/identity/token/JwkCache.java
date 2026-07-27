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

package stroom.security.identity.token;

import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jose4j.jwk.PublicJsonWebKey;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class JwkCache implements PublicJsonWebKeyProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JwkCache.class);

    private static final String KEY = "key";
    private final LoadingCache<String, Keys> cache;

    @Inject
    JwkCache(final JwkDao jwkDao) {
        cache = Caffeine.newBuilder()
                .maximumSize(100)
                .refreshAfterWrite(1, TimeUnit.MINUTES)
                .build(k -> {
                    // Ask for the active key first, so that on an empty table its lazy create runs
                    // before listPublishable() looks, and only one key is made.
                    final PublicJsonWebKey active = jwkDao.getActiveKey();
                    return new Keys(active, jwkDao.listPublishable());
                });
    }

    /**
     * The publishable keys, for the JWKS endpoint and for verification.
     */
    @Override
    public List<PublicJsonWebKey> list() {
        return cache.get(KEY).publishable();
    }

    /**
     * The key to sign with. Deliberately not {@code list().get(0)}; that could be a retired key.
     */
    @Override
    public PublicJsonWebKey getActiveKey() {
        return cache.get(KEY).active();
    }

    private record Keys(PublicJsonWebKey active, List<PublicJsonWebKey> publishable) {

    }
}
