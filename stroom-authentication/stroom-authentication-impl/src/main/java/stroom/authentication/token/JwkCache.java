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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.jose4j.jwk.PublicJsonWebKey;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JwkCache {
    private static final String KEY = "key";
    private final LoadingCache<String, List<PublicJsonWebKey>> cache;

    @Inject
    JwkCache(final JwkDao jwkDao) {
        cache = Caffeine.newBuilder()
                .maximumSize(100)
                .refreshAfterWrite(1, TimeUnit.MINUTES)
                .build(k -> jwkDao.readJwk());
    }

    public List<PublicJsonWebKey> get() {
        return cache.get(KEY);
    }
}
