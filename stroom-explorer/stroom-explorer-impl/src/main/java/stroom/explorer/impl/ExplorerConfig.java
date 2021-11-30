package stroom.explorer.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class ExplorerConfig extends AbstractConfig implements HasDbConfig {

    private final ExplorerDbConfig dbConfig;
    private final CacheConfig docRefInfoCache;

    public ExplorerConfig() {
        dbConfig = new ExplorerDbConfig();
        docRefInfoCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ExplorerConfig(@JsonProperty("db") final ExplorerDbConfig dbConfig,
                          @JsonProperty("docRefInfoCache") final CacheConfig docRefInfoCache) {
        this.dbConfig = dbConfig;
        this.docRefInfoCache = docRefInfoCache;
    }

    @Override
    @JsonProperty("db")
    public ExplorerDbConfig getDbConfig() {
        return dbConfig;
    }


    @JsonProperty("docRefInfoCache")
    public CacheConfig getDocRefInfoCache() {
        return docRefInfoCache;
    }

    public static class ExplorerDbConfig extends AbstractDbConfig {

        public ExplorerDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public ExplorerDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
