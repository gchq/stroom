package stroom.search.elastic.search;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticSearchConfig extends AbstractConfig implements IsStroomConfig {

    private final StroomDuration scrollDuration;
    private final String storeSize;
    private final CacheConfig searchResultCache;

    public ElasticSearchConfig() {
        scrollDuration = StroomDuration.ofMinutes(1);
        storeSize = "1000000,100,10,1";
        searchResultCache = CacheConfig.builder()
                .maximumSize(10000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticSearchConfig(@JsonProperty("scrollDuration") final StroomDuration scrollDuration,
                               @JsonProperty("storeSize") final String storeSize,
                               @JsonProperty("searchResultCache") final CacheConfig searchResultCache) {
        this.scrollDuration = scrollDuration;
        this.storeSize = storeSize;
        this.searchResultCache = searchResultCache;
    }

    @JsonPropertyDescription("Amount of time to allow an Elasticsearch scroll request to continue before aborting.")
    public StroomDuration getScrollDuration() {
        return scrollDuration;
    }

    @JsonPropertyDescription("The maximum number of search results to keep in memory at each level.")
    public String getStoreSize() {
        return storeSize;
    }

    public CacheConfig getSearchResultCache() {
        return searchResultCache;
    }

    @Override
    public String toString() {
        return "ElasticSearchConfig{" +
                "scrollDuration='" + scrollDuration + "'" +
                ", storeSize=" + storeSize +
                ", searchResultCache=" + searchResultCache +
                '}';
    }
}
