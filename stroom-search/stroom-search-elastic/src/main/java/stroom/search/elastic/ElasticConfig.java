package stroom.search.elastic;

import stroom.search.elastic.indexing.ElasticIndexingConfig;
import stroom.search.elastic.search.ElasticSearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticConfig extends AbstractConfig implements IsStroomConfig {

    public final ElasticClientConfig clientConfig;
    private final ElasticIndexingConfig indexingConfig;
    private final ElasticSearchConfig searchConfig;
    private final CacheConfig indexClientCache;
    private final CacheConfig indexCache;

    public ElasticConfig() {
        clientConfig = new ElasticClientConfig();
        indexingConfig = new ElasticIndexingConfig();
        searchConfig = new ElasticSearchConfig();
        indexClientCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        indexCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticConfig(@JsonProperty("client") final ElasticClientConfig clientConfig,
                         @JsonProperty("indexing") final ElasticIndexingConfig indexingConfig,
                         @JsonProperty("search") final ElasticSearchConfig searchConfig,
                         @JsonProperty("indexClientCache") final CacheConfig indexClientCache,
                         @JsonProperty("indexCache") final CacheConfig indexCache) {
        this.clientConfig = clientConfig;
        this.indexingConfig = indexingConfig;
        this.searchConfig = searchConfig;
        this.indexClientCache = indexClientCache;
        this.indexCache = indexCache;
    }

    @JsonProperty("client")
    public ElasticClientConfig getClientConfig() {
        return clientConfig;
    }

    @JsonProperty("indexing")
    public ElasticIndexingConfig getIndexingConfig() {
        return indexingConfig;
    }

    @JsonProperty("search")
    public ElasticSearchConfig getSearchConfig() {
        return searchConfig;
    }

    public CacheConfig getIndexClientCache() {
        return indexClientCache;
    }

    public CacheConfig getIndexCache() {
        return indexCache;
    }

    @Override
    public String toString() {
        return "ElasticConfig{" +
                "clientConfig=" + clientConfig +
                ", indexingConfig=" + indexingConfig +
                ", searchConfig=" + searchConfig +
                ", indexClientCache=" + indexClientCache +
                ", indexCache=" + indexCache +
                '}';
    }
}
