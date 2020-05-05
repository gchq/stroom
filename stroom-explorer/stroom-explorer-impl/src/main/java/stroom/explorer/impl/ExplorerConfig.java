package stroom.explorer.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class ExplorerConfig extends AbstractConfig implements HasDbConfig {
    private DbConfig dbConfig = new DbConfig();

    private CacheConfig docRefInfoCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @JsonProperty("docRefInfoCache")
    public CacheConfig getDocRefInfoCache() {
        return docRefInfoCache;
    }

    public void setDocRefInfoCache(final CacheConfig docRefInfoCache) {
        this.docRefInfoCache = docRefInfoCache;
    }
}
