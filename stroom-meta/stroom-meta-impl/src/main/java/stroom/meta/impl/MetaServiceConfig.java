package stroom.meta.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


public class MetaServiceConfig extends AbstractConfig implements HasDbConfig {

    private final MetaServiceDbConfig dbConfig;
    private final MetaValueConfig metaValueConfig;
    private final CacheConfig metaFeedCache;
    private final CacheConfig metaProcessorCache;
    private final CacheConfig metaTypeCache;
    private final String metaTypes;

    public MetaServiceConfig() {
        dbConfig = new MetaServiceDbConfig();
        metaValueConfig = new MetaValueConfig();
        metaFeedCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        metaProcessorCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        metaTypeCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        metaTypes = "Raw Events\nRaw Reference\nEvents\nReference\nRecords\nError";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public MetaServiceConfig(@JsonProperty("db") final MetaServiceDbConfig dbConfig,
                             @JsonProperty("metaValue") final MetaValueConfig metaValueConfig,
                             @JsonProperty("metaFeedCache") final CacheConfig metaFeedCache,
                             @JsonProperty("metaProcessorCache") final CacheConfig metaProcessorCache,
                             @JsonProperty("metaTypeCache") final CacheConfig metaTypeCache,
                             @JsonProperty("metaTypes") final String metaTypes) {
        this.dbConfig = dbConfig;
        this.metaValueConfig = metaValueConfig;
        this.metaFeedCache = metaFeedCache;
        this.metaProcessorCache = metaProcessorCache;
        this.metaTypeCache = metaTypeCache;
        this.metaTypes = metaTypes;
    }

    @Override
    @JsonProperty("db")
    public MetaServiceDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonProperty("metaValue")
    public MetaValueConfig getMetaValueConfig() {
        return metaValueConfig;
    }

    public CacheConfig getMetaFeedCache() {
        return metaFeedCache;
    }

    public CacheConfig getMetaProcessorCache() {
        return metaProcessorCache;
    }

    public CacheConfig getMetaTypeCache() {
        return metaTypeCache;
    }

    @JsonPropertyDescription("List of accepted meta type names.")
    public String getMetaTypes() {
        return metaTypes;
    }

    public static class MetaServiceDbConfig extends AbstractDbConfig {

        public MetaServiceDbConfig() {
            super();
        }

        @JsonCreator
        public MetaServiceDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
