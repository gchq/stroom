package stroom.elastic.impl;

import stroom.util.cache.CacheConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class ElasticConfig {
    private CacheConfig elasticIndexConfigCache = new CacheConfig.Builder()
            .maximumSize(100L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
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