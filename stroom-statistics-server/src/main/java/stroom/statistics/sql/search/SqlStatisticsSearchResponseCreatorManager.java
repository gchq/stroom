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

package stroom.statistics.sql.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Clearable;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.util.lifecycle.StroomFrequencySchedule;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@SuppressWarnings("unused") // used by DI
@Singleton
public class SqlStatisticsSearchResponseCreatorManager implements SearchResponseCreatorManager, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticsSearchResponseCreatorManager.class);

    private final SearchResponseCreatorCache cache;

    @Inject
    public SqlStatisticsSearchResponseCreatorManager(
            final SqlStatisticsInMemorySearchResponseCreatorCacheFactory cacheFactory,
            final SqlStatisticStoreFactory storeFactory) {

        // Create a cache using the supplied cacheFactory, providing it the storeFactory for it
        // to use when creating new cache entries
        this.cache = cacheFactory.create(storeFactory);
    }

    /**
     * Get a {@link SearchResponseCreator} from the cache or create one if it doesn't exist
     */
    public SearchResponseCreator get(final SearchResponseCreatorCache.Key key) {
        LOGGER.debug("get called for key {}", key);
        return cache.get(key);
    }

    /**
     * Remove an entry from the cache, this will also terminate any running search for that entry
     */
    public void remove(final SearchResponseCreatorCache.Key key) {
        LOGGER.debug("remove called for key {}", key);

        cache.remove(key);
    }

    @SuppressWarnings("unused") //called by stroom lifecycle
    @StroomFrequencySchedule("10s")
    public void evictExpiredElements() {
        cache.evictExpiredElements();
    }

    @Override
    public void clear() {
        cache.clear();
    }
}