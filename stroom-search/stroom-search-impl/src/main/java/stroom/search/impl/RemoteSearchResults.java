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

package stroom.search.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.index.impl.IndexShardSearchConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
class RemoteSearchResults {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchResults.class);

    private final StroomCache<String, RemoteSearchResultFactory> cache;

    @Inject
    RemoteSearchResults(final CacheManager cacheManager,
                        final Provider<IndexShardSearchConfig> searchConfigProvider) {
        cache = cacheManager.create(
                "Remote search results",
                () -> searchConfigProvider.get().getRemoteSearchResultCache(),
                (k, v) ->
                        v.destroy());
    }

    public Optional<RemoteSearchResultFactory> get(final String key) {
        LOGGER.trace(() -> "get() " + key);
        return cache.getIfPresent(key);
    }

    public void put(final String key, final RemoteSearchResultFactory factory) {
        LOGGER.trace(() -> "put() " + key);
        cache.put(key, factory);
    }

    public void invalidate(final String key) {
        LOGGER.trace(() -> "invalidate() " + key, new RuntimeException("invalidate"));
        cache.invalidate(key);
    }
}
