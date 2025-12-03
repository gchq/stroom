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

import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.JsonWebKeyFactory;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jose4j.jwk.PublicJsonWebKey;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class JwkCache implements PublicJsonWebKeyProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JwkCache.class);

    private static final String KEY = "key";
    private final LoadingCache<String, List<PublicJsonWebKey>> cache;

    @Inject
    JwkCache(final JwkDao jwkDao,
             final DefaultOpenIdCredentials defaultOpenIdCredentials,
             final JsonWebKeyFactory jsonWebKeyFactory,
             final Provider<AbstractOpenIdConfig> openIdConfigProvider) {

        cache = Caffeine.newBuilder()
                .maximumSize(100)
                .refreshAfterWrite(1, TimeUnit.MINUTES)
                .build(k -> {
                    // Bypass the DB when we are using test default creds, i.e. in a test/demo
                    // environment. Not for prod use.
                    if (IdpType.TEST_CREDENTIALS.equals(openIdConfigProvider.get().getIdentityProviderType())) {
                        LOGGER.debug("Using default public json web key");
                        return Collections.singletonList(
                                jsonWebKeyFactory.fromJson(defaultOpenIdCredentials.getPublicKeyJson()));
                    } else {
                        return jwkDao.readJwk();
                    }
                });
    }

    @Override
    public List<PublicJsonWebKey> list() {
        return cache.get(KEY);
    }

    @Override
    public PublicJsonWebKey getFirst() {
        return list().get(0);
    }
}
