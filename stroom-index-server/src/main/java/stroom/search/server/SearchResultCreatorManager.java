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

import org.springframework.stereotype.Component;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;
import javax.inject.Named;

@Component
public class SearchResultCreatorManager {
//    private static final int MAX_ACTIVE_QUERIES = 10000;

//    private final StoreFactory storeFactory;
//    private final LoadingCache<SearchResultCreatorManager.Key, SearchResponseCreator> cache;

    private final SearchResultCreatorCache cache;

//    @Inject
//    @SuppressWarnings("unchecked")
//    public SearchResultCreatorManager(final CacheManager cacheManager,
//                                      final StoreFactory storeFactory) {
//        this.storeFactory = storeFactory;
//
//        final RemovalListener<SearchResultCreatorManager.Key, SearchResponseCreator> removalListener = notification ->
//                destroy(notification.getKey(), notification.getValue());
//        final CacheLoader<SearchResultCreatorManager.Key, SearchResponseCreator> cacheLoader = CacheLoader.from(this::create);
//        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
//                .maximumSize(MAX_ACTIVE_QUERIES)
//                .expireAfterAccess(10, TimeUnit.MINUTES)
//                .removalListener(removalListener);
//        cache = cacheBuilder.build(cacheLoader);
//        cacheManager.registerCache("Search Result Creators", cacheBuilder, cache);
//    }

    @Inject
    public SearchResultCreatorManager(
            @Named("luceneInMemorySearchResultCreatorCacheFactory") final SearchResultCreatorCacheFactory cacheFactory,
            @Named("luceneSearchStoreFactory") final StoreFactory storeFactory) {

        SearchResultCreatorCache cache = cacheFactory.create(storeFactory);
        this.cache = cache;
    }

    public SearchResponseCreator get(final SearchResultCreatorCache.Key key) {
        return cache.get(key);
    }

    public void remove(final SearchResultCreatorCache.Key key) {
        cache.remove(key);
    }

//    private SearchResponseCreator create(final SearchResultCreatorManager.Key key) {
//        Store store = storeFactory.create(key.searchRequest);
//        return new SearchResponseCreator(store);
//    }
//
//    private void destroy(final Key key, final SearchResponseCreator value) {
//        if (value != null) {
//            value.destroy();
//        }
//    }

    @StroomFrequencySchedule("10s")
    public void evictExpiredElements() {
        cache.evictExpiredElements();
    }

//    public static class Key {
//        private final QueryKey queryKey;
//        private final SearchRequest searchRequest;
//
//        public Key(final QueryKey queryKey) {
//            this.queryKey = queryKey;
//            this.searchRequest = null;
//        }
//
//        public Key(final SearchRequest searchRequest) {
//            this.queryKey = searchRequest.getKey();
//            this.searchRequest = searchRequest;
//        }
//
//        @Override
//        public boolean equals(final Object o) {
//            if (this == o) return true;
//            if (o == null || getClass() != o.getClass()) return false;
//
//            final Key key = (Key) o;
//
//            return queryKey.equals(key.queryKey);
//        }
//
//        @Override
//        public int hashCode() {
//            return queryKey.hashCode();
//        }
//
//        @Override
//        public String toString() {
//            return "Key{" +
//                    "queryKey=" + queryKey +
//                    '}';
//        }
//    }
}