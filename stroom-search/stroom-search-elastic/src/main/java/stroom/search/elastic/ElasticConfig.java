package stroom.search.elastic;

import stroom.search.elastic.search.ElasticSearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class ElasticConfig extends AbstractConfig implements IsStroomConfig {

    private ElasticSearchConfig elasticSearchConfig = new ElasticSearchConfig();
    private CacheConfig indexClientCache = CacheConfig.builder()
            .maximumSize(100L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();
    private CacheConfig indexCache = CacheConfig.builder()
            .maximumSize(100L)
            .expireAfterWrite(StroomDuration.ofMinutes(10))
            .build();

    @JsonProperty("search")
    public ElasticSearchConfig getElasticSearchConfig() {
        return elasticSearchConfig;
    }

    public void setElasticSearchConfig(final ElasticSearchConfig elasticSearchConfig) {
        this.elasticSearchConfig = elasticSearchConfig;
    }

    public CacheConfig getIndexClientCache() {
        return indexClientCache;
    }

    public void setIndexClientCache(final CacheConfig indexClientCache) {
        this.indexClientCache = indexClientCache;
    }

    public CacheConfig getIndexCache() {
        return indexCache;
    }

    public void setIndexCache(final CacheConfig indexCache) {
        this.indexCache = indexCache;
    }

    @Override
    public String toString() {
        return "ElasticConfig{" +
                "elasticSearchConfig=" + elasticSearchConfig +
                ", indexClientCache=" + indexClientCache +
                ", indexCache=" + indexCache +
                '}';
    }
}
