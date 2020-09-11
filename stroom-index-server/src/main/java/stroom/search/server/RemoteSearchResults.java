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

package stroom.search.server;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.springframework.stereotype.Component;
import stroom.query.api.v2.QueryKey;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
public class RemoteSearchResults {
    private static final int MAX_ACTIVE_QUERIES = 100;
    private final Cache<QueryKey, RemoteSearchResultFactory> cache;

    @Inject
    public RemoteSearchResults(final CacheManager cacheManager) {
        final RemovalListener<QueryKey, RemoteSearchResultFactory> removalListener = notification ->
                notification.getValue().destroy();

//        final CacheLoader<String, RemoteSearchResultFactory> cacheLoader = CacheLoader.from(k -> new ActiveQueries(dataSourceProviderRegistry, securityContext));
        final CacheBuilder<QueryKey, RemoteSearchResultFactory> cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_ACTIVE_QUERIES)
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .removalListener(removalListener);
        cache = cacheBuilder.build();
        cacheManager.registerCache("Remote search results", cacheBuilder, cache);
    }

    public RemoteSearchResultFactory get(final QueryKey key) {
        return cache.getIfPresent(key);
    }

    public void put(final QueryKey key, final RemoteSearchResultFactory factory) {
        cache.put(key, factory);
    }

    public void invalidate(final QueryKey key) {
        cache.invalidate(key);
    }
}
