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

    public final ElasticClientConfig elasticClientConfig;
    private final ElasticIndexingConfig elasticIndexingConfig;
    private final ElasticSearchConfig elasticSearchConfig;
    private final CacheConfig indexClientCache;
    private final CacheConfig indexCache;

    public ElasticConfig() {
        elasticClientConfig = new ElasticClientConfig();
        elasticIndexingConfig = new ElasticIndexingConfig();
        elasticSearchConfig = new ElasticSearchConfig();
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
    public ElasticConfig(@JsonProperty("client") final ElasticClientConfig elasticClientConfig,
                         @JsonProperty("indexing") final ElasticIndexingConfig elasticIndexingConfig,
                         @JsonProperty("search") final ElasticSearchConfig elasticSearchConfig,
                         @JsonProperty("indexClientCache") final CacheConfig indexClientCache,
                         @JsonProperty("indexCache") final CacheConfig indexCache) {
        this.elasticClientConfig = elasticClientConfig;
        this.elasticIndexingConfig = elasticIndexingConfig;
        this.elasticSearchConfig = elasticSearchConfig;
        this.indexClientCache = indexClientCache;
        this.indexCache = indexCache;
    }

    @JsonProperty("client")
    public ElasticClientConfig getElasticClientConfig() {
        return elasticClientConfig;
    }

    @JsonProperty("indexing")
    public ElasticIndexingConfig getElasticIndexingConfig() {
        return elasticIndexingConfig;
    }

    @JsonProperty("search")
    public ElasticSearchConfig getElasticSearchConfig() {
        return elasticSearchConfig;
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
                "elasticClientConfig=" + elasticClientConfig +
                ", elasticIndexingConfig=" + elasticIndexingConfig +
                ", elasticSearchConfig=" + elasticSearchConfig +
                ", indexClientCache=" + indexClientCache +
                ", indexCache=" + indexCache +
                '}';
    }
}
