package stroom.search.solr;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.search.solr.search.SolrSearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class SolrConfig extends IsConfig {
    private SolrSearchConfig solrSearchConfig = new SolrSearchConfig();
    private CacheConfig indexClientCache = new CacheConfig.Builder()
            .maximumSize(100L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private CacheConfig indexCache = new CacheConfig.Builder()
            .maximumSize(100L)
            .expireAfterWrite(10, TimeUnit.MINUTES)
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
