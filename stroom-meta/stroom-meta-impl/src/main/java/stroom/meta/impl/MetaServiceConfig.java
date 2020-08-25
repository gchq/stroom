package stroom.meta.impl;

import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class MetaServiceConfig extends AbstractConfig implements HasDbConfig {
    private DbConfig dbConfig = new DbConfig();
    private MetaValueConfig metaValueConfig = new MetaValueConfig();
    private CacheConfig metaFeedCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();
    private CacheConfig metaProcessorCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();
    private CacheConfig metaTypeCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();
    private volatile String metaTypes = "Raw Events\nRaw Reference\nEvents\nReference\nRecords\nError";

    private volatile List<String> metaTypeList;

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @JsonProperty("metaValue")
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

    @JsonPropertyDescription("List of accepted meta type names")
    public String getMetaTypes() {
        return metaTypes;
    }

    public void setMetaTypes(final String metaTypes) {
        this.metaTypes = metaTypes;
        metaTypeList = null;
    }

    @JsonIgnore
    public List<String> getMetaTypeList() {
        List<String> list = metaTypeList;
        if (list == null) {
            final String mt = metaTypes;
            if (mt != null) {
                list = Arrays
                        .stream(mt.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                metaTypeList = list;
            }
        }
        return list;
    }
}
