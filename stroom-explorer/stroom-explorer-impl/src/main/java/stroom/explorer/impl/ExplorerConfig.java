package stroom.explorer.impl;

import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class ExplorerConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private DbConfig dbConfig = new DbConfig();

    private CacheConfig docRefInfoCache = CacheConfig.builder()
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
