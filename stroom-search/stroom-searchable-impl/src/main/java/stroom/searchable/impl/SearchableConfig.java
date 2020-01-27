package stroom.searchable.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class SearchableConfig extends AbstractConfig {
    private String storeSize = "1000000,100,10,1";
    private CacheConfig searchResultCache = new CacheConfig.Builder()
            .maximumSize(10000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    @JsonPropertyDescription("The maximum number of search results to keep in memory at each level.")
    public String getStoreSize() {
        return storeSize;
    }

    public void setStoreSize(final String storeSize) {
        this.storeSize = storeSize;
    }

    public CacheConfig getSearchResultCache() {
        return searchResultCache;
    }

    public void setSearchResultCache(final CacheConfig searchResultCache) {
        this.searchResultCache = searchResultCache;
    }

    @Override
    public String toString() {
        return "SearchableConfig{" +
                "storeSize='" + storeSize + '\'' +
                ", searchResultCache=" + searchResultCache +
                '}';
    }
}
