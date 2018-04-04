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

package stroom.statistics.server.sql.search;

import org.springframework.stereotype.Component;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorCacheFactory;
import stroom.query.common.v2.StoreFactory;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;
import javax.inject.Named;

@Component
public class SqlStatisticsSearchResponseCreatorManager {

    private final SearchResponseCreatorCache cache;

    @Inject
    public SqlStatisticsSearchResponseCreatorManager(
            @Named("sqlStatisticsInMemorySearchResponseCreatorCacheFactory") final SearchResponseCreatorCacheFactory cacheFactory,
            @Named("sqlStatisticStoreFactory") final StoreFactory storeFactory) {

        // Create a cache using the supplied cacheFactory, providing it the storeFactory for it
        // to use when creating new cache entries
        SearchResponseCreatorCache cache = cacheFactory.create(storeFactory);
        this.cache = cache;
    }

    /**
     * Get a {@link SearchResponseCreator} from the cache or create one if it doesn't exist
     */
    public SearchResponseCreator get(final SearchResponseCreatorCache.Key key) {
        return cache.get(key);
    }

    /**
     * Remove an entry from the cache, this will also terminate any running search for that entry
     */
    public void remove(final SearchResponseCreatorCache.Key key) {
        cache.remove(key);
    }

    @SuppressWarnings("unused") //called by stroom lifecycle
    @StroomFrequencySchedule("10s")
    public void evictExpiredElements() {
        cache.evictExpiredElements();
    }
}