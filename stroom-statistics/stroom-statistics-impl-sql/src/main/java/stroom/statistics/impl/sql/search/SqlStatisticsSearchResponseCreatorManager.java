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

package stroom.statistics.impl.sql.search;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorCache.Key;
import stroom.query.common.v2.SearchResponseCreatorFactory;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.query.common.v2.Store;
import stroom.util.shared.Clearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@SuppressWarnings("unused") //Used by DI
@Singleton
class SqlStatisticsSearchResponseCreatorManager implements SearchResponseCreatorManager, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticsSearchResponseCreatorManager.class);

    private static final String CACHE_NAME = "SQL Statistics Search Result Creators";

    private final SqlStatisticStoreFactory storeFactory;
    private final SearchResponseCreatorFactory searchResponseCreatorFactory;
    private final ICache<Key, SearchResponseCreator> cache;

    @Inject
    public SqlStatisticsSearchResponseCreatorManager(final CacheManager cacheManager,
                                                     final SearchConfig searchConfig,
                                                     final SqlStatisticStoreFactory storeFactory,
                                                     final SearchResponseCreatorFactory searchResponseCreatorFactory) {
        this.storeFactory = storeFactory;
        this.searchResponseCreatorFactory = searchResponseCreatorFactory;
        cache = cacheManager.create(CACHE_NAME, searchConfig::getSearchResultCache, this::create, this::destroy);
    }

    private SearchResponseCreator create(SearchResponseCreatorCache.Key key) {
        try {
            LOGGER.debug("Creating new store for key {}", key);
            final Store store = storeFactory.create(key.getSearchRequest());
            return searchResponseCreatorFactory.create(store);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    private void destroy(final Key key, final SearchResponseCreator value) {
        value.destroy();
    }

    @Override
    public SearchResponseCreator get(final SearchResponseCreatorCache.Key key) {
        return cache.get(key);
    }

    @Override
    public void remove(final SearchResponseCreatorCache.Key key) {
        cache.remove(key);
    }

    @Override
    public void evictExpiredElements() {
        cache.evictExpiredElements();
    }

    @Override
    public void clear() {
        cache.clear();
    }
}