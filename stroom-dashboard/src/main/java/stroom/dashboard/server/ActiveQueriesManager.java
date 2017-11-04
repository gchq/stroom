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

package stroom.dashboard.server;

import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.springframework.stereotype.Component;
import stroom.cache.Loader;
import stroom.util.cache.CentralCacheManager;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
public class ActiveQueriesManager {
    private static final int MAX_ACTIVE_QUERIES = 1000;

    private final Cache<String, ActiveQueries> cache;

    @Inject
    public ActiveQueriesManager(final CentralCacheManager cacheManager) {
        final Loader<String, ActiveQueries> loader = new Loader<String, ActiveQueries>() {
            @Override
            public ActiveQueries load(final String key) throws Exception {
                return new ActiveQueries();
            }
        };

        final CacheConfiguration<String, ActiveQueries> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, ActiveQueries.class,
                ResourcePoolsBuilder.heap(MAX_ACTIVE_QUERIES))
                .withExpiry(Expirations.timeToIdleExpiration(Duration.of(1, TimeUnit.MINUTES)))
                .withLoaderWriter(loader)
                .build();

        cache = cacheManager.createCache("Active Queries", cacheConfiguration);
    }

    public ActiveQueries get(final String key) {
        return cache.get(key);
    }
}
