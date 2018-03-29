package stroom.search.server;

import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.SearchResponseCreator;

public interface SearchResultCreatorCache {

    SearchResponseCreator get(final Key key);

    void remove(final Key key);

    void evictExpiredElements();


    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    class Key {
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

        public QueryKey getQueryKey() {
            return queryKey;
        }

        public SearchRequest getSearchRequest() {
            return searchRequest;
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
