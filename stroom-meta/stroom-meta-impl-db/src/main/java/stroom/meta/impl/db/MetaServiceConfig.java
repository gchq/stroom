package stroom.meta.impl.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class MetaServiceConfig extends AbstractConfig implements HasDbConfig {
    private DbConfig dbConfig = new DbConfig();
    private MetaValueConfig metaValueConfig = new MetaValueConfig();
    private CacheConfig metaFeedCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private CacheConfig metaProcessorCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private CacheConfig metaTypeCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public MetaValueConfig getMetaValueConfig() {
        return metaValueConfig;
    }

    public void setMetaValueConfig(final MetaValueConfig metaValueConfig) {
        this.metaValueConfig = metaValueConfig;
    }

    public CacheConfig getMetaFeedCache() {
        return metaFeedCache;
    }

    public void setMetaFeedCache(final CacheConfig metaFeedCache) {
        this.metaFeedCache = metaFeedCache;
    }

    public CacheConfig getMetaProcessorCache() {
        return metaProcessorCache;
    }

    public void setMetaProcessorCache(final CacheConfig metaProcessorCache) {
        this.metaProcessorCache = metaProcessorCache;
    }

    public CacheConfig getMetaTypeCache() {
        return metaTypeCache;
    }

    public void setMetaTypeCache(final CacheConfig metaTypeCache) {
        this.metaTypeCache = metaTypeCache;
    }
}
