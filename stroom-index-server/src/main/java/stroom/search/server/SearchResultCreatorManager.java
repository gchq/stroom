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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import org.springframework.stereotype.Component;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.Store;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
public class SearchResultCreatorManager {
    private static final int MAX_ACTIVE_QUERIES = 10000;

    //TODO Need a StoreFactory interface in query-common implemented by LuceneSearchStoreFactory and SqlStatisticsStoreFactory
    //TODO Then this class could go into query-common, with ctor taking a StoreFactory
    //TODO Would need a mechanism for specifing props like TTL and MAX_ACTIVE_QUERIES in the ctor
    //TODO Would need to get rid of StroomFrequencySchedule anno and let the calling code do that
    //TODO May want an abstraction for the caching impl so we can replace it with something disk backed like LMDB

    private final LuceneSearchStoreFactory luceneSearchStoreFactory;
    private final LoadingCache<SearchResultCreatorManager.Key, SearchResponseCreator> cache;

    @Inject
    @SuppressWarnings("unchecked")
    public SearchResultCreatorManager(final CacheManager cacheManager,
                                      final LuceneSearchStoreFactory luceneSearchStoreFactory) {
        this.luceneSearchStoreFactory = luceneSearchStoreFactory;

        final RemovalListener<SearchResultCreatorManager.Key, SearchResponseCreator> removalListener = notification ->
                destroy(notification.getKey(), notification.getValue());
        final CacheLoader<SearchResultCreatorManager.Key, SearchResponseCreator> cacheLoader = CacheLoader.from(this::create);
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_ACTIVE_QUERIES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .removalListener(removalListener);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Search Result Creators", cacheBuilder, cache);
    }

    public SearchResponseCreator get(final SearchResultCreatorManager.Key key) {
        return cache.getUnchecked(key);
    }

    public void remove(final SearchResultCreatorManager.Key key) {
        cache.invalidate(key);
        cache.cleanUp();
    }

    private SearchResponseCreator create(final SearchResultCreatorManager.Key key) {
        Store store = luceneSearchStoreFactory.create(key.searchRequest);
        return new SearchResponseCreator(store);
    }

    private void destroy(final Key key, final SearchResponseCreator value) {
        if (value != null) {
            value.destroy();
        }
    }

    @StroomFrequencySchedule("10s")
    public void evictExpiredElements() {
        cache.cleanUp();
    }

    public static class Key {
        private final QueryKey queryKey;
        private final SearchRequest searchRequest;

        public Key(final QueryKey queryKey) {
            this.queryKey = queryKey;
            this.searchRequest = null;
        }

        public Key(final SearchRequest searchRequest) {
            this.queryKey = searchRequest.getKey();
            this.searchRequest = searchRequest;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Key key = (Key) o;

            return queryKey.equals(key.queryKey);
        }

        @Override
        public int hashCode() {
            return queryKey.hashCode();
        }

        @Override
        public String toString() {
            return "Key{" +
                    "queryKey=" + queryKey +
                    '}';
        }
    }
}