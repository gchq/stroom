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

package stroom.search.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.query.api.v2.QueryKey;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
class RemoteSearchResults {
    private final ICache<QueryKey, RemoteSearchResultFactory> cache;

    @Inject
    RemoteSearchResults(final CacheManager cacheManager, final SearchConfig searchConfig) {
        cache = cacheManager.create("Remote search results", searchConfig::getResultStoreCache, null, (k, v) ->
                v.destroy());
    }

    public Optional<RemoteSearchResultFactory> get(final QueryKey key) {
        return cache.getOptional(key);
    }

    public void put(final QueryKey key, final RemoteSearchResultFactory factory) {
        cache.put(key, factory);
    }

    public void invalidate(final QueryKey key) {
        cache.invalidate(key);
    }
}
