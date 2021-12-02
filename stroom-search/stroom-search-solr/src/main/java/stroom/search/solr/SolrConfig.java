package stroom.search.solr;

import stroom.search.solr.search.SolrSearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class SolrConfig extends AbstractConfig {

    private final SolrSearchConfig solrSearchConfig;
    private final CacheConfig indexClientCache;
    private final CacheConfig indexCache;

    public SolrConfig() {
        solrSearchConfig = new SolrSearchConfig();
        indexClientCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        indexCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
    }

    @JsonCreator
    public SolrConfig(@JsonProperty("search") final SolrSearchConfig solrSearchConfig,
                      @JsonProperty("indexClientCache") final CacheConfig indexClientCache,
                      @JsonProperty("indexCache") final CacheConfig indexCache) {
        this.solrSearchConfig = solrSearchConfig;
        this.indexClientCache = indexClientCache;
        this.indexCache = indexCache;
    }

    @JsonProperty("search")
    public SolrSearchConfig getSolrSearchConfig() {
        return solrSearchConfig;
    }

    public CacheConfig getIndexClientCache() {
        return indexClientCache;
    }

    public CacheConfig getIndexCache() {
        return indexCache;
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
