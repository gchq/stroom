package stroom.elastic.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class ElasticConfig {
    private CacheConfig elasticIndexConfigCache = new CacheConfig.Builder()
            .maximumSize(100L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    public CacheConfig getElasticIndexConfigCache() {
        return elasticIndexConfigCache;
    }

    public void setElasticIndexConfigCache(final CacheConfig elasticIndexConfigCache) {
        this.elasticIndexConfigCache = elasticIndexConfigCache;
    }

    @Override
    public String toString() {
        return "ElasticConfig{" +
                "elasticIndexConfigCache=" + elasticIndexConfigCache +
                '}';
    }
}