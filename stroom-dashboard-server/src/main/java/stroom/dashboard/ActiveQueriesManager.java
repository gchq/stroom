/*
 * Copyright 2017 Crown Copyright
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

class ActiveQueriesManager {
    private static final int MAX_ACTIVE_QUERIES = 100;

    private final LoadingCache<String, ActiveQueries> cache;

    @Inject
    @SuppressWarnings("unchecked")
    ActiveQueriesManager(final CacheManager cacheManager,
                         final DataSourceProviderRegistry dataSourceProviderRegistry) {
        final RemovalListener<String, ActiveQueries> removalListener = notification -> notification.getValue().destroy();

        final CacheLoader<String, ActiveQueries> cacheLoader = CacheLoader.from(k -> new ActiveQueries(dataSourceProviderRegistry));
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_ACTIVE_QUERIES)
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .removalListener(removalListener);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Active Queries", cacheBuilder, cache);
    }

    public ActiveQueries get(final String key) {
        return cache.getUnchecked(key);
    }
}
