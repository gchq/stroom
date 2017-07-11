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

import net.sf.ehcache.CacheManager;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.query.SearchResponseCreator;
import stroom.query.api.v1.QueryKey;
import stroom.query.api.v1.SearchRequest;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;

@Component
public class SearchResultCreatorManager extends AbstractCacheBean<SearchResultCreatorManager.Key, SearchResponseCreator> {
    private static final int MAX_ACTIVE_QUERIES = 10000;

    private final LuceneSearchStoreFactory luceneSearchStoreFactory;

    @Inject
    public SearchResultCreatorManager(final CacheManager cacheManager, final LuceneSearchStoreFactory luceneSearchStoreFactory) {
        super(cacheManager, "Search Result Creators", MAX_ACTIVE_QUERIES);
        this.luceneSearchStoreFactory = luceneSearchStoreFactory;
    }

    @Override
    protected SearchResponseCreator create(final SearchResultCreatorManager.Key key) {
        return new SearchResponseCreator(luceneSearchStoreFactory.create(key.searchRequest));
    }

    @Override
    protected void destroy(final Key key, final SearchResponseCreator value) {
        super.destroy(key, value);
        if (value != null) {
            value.destroy();
        }
    }

    @Override
    @StroomFrequencySchedule("10s")
    public void evictExpiredElements() {
        super.evictExpiredElements();
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