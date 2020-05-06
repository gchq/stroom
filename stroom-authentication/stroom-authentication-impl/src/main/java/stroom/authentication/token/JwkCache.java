/*
 * Copyright 2020 Crown Copyright
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

package stroom.authentication.token;

import stroom.authentication.api.JsonWebKeyFactory;
import stroom.authentication.config.AuthenticationConfig;
import stroom.util.authentication.DefaultOpenIdCredentials;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.jose4j.jwk.PublicJsonWebKey;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class JwkCache {
    private static final String KEY = "key";
    private final LoadingCache<String, List<PublicJsonWebKey>> cache;

    @Inject
    JwkCache(final JwkDao jwkDao,
             final AuthenticationConfig authenticationConfig,
             final DefaultOpenIdCredentials defaultOpenIdCredentials,
             final JsonWebKeyFactory jsonWebKeyFactory) {

        cache = Caffeine.newBuilder()
                .maximumSize(100)
                .refreshAfterWrite(1, TimeUnit.MINUTES)
                .build(k -> {
                    // Bypass the DB when we are using test default creds, i.e. in a test/demo
                    // environment. Not for prod use.
                    if (authenticationConfig.isUseDefaultOpenIdCredentials()) {
                        return Collections.singletonList(
                                jsonWebKeyFactory.fromJson(defaultOpenIdCredentials.getPublicKeyJson()));
                    } else {
                        return jwkDao.readJwk();
                    }
                });
    }

    public List<PublicJsonWebKey> get() {
        return cache.get(KEY);
    }
}
