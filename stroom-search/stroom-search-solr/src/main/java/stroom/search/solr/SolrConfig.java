package stroom.search.solr;

import stroom.search.solr.search.SolrSearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

public class SolrConfig extends AbstractConfig {

    private SolrSearchConfig solrSearchConfig = new SolrSearchConfig();
    private CacheConfig indexClientCache = CacheConfig.builder()
            .maximumSize(100L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();
    private CacheConfig indexCache = CacheConfig.builder()
            .maximumSize(100L)
            .expireAfterWrite(StroomDuration.ofMinutes(10))
            .build();

    @JsonProperty("search")
    public SolrSearchConfig getSolrSearchConfig() {
        return solrSearchConfig;
    }

    public void setSolrSearchConfig(final SolrSearchConfig solrSearchConfig) {
        this.solrSearchConfig = solrSearchConfig;
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
        return "SolrConfig{" +
                "solrSearchConfig=" + solrSearchConfig +
                ", indexClientCache=" + indexClientCache +
                ", indexCache=" + indexCache +
                '}';
    }
}
