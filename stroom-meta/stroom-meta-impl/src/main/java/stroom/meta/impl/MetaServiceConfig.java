package stroom.meta.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.data.shared.StreamTypeNames;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.validation.IsSupersetOf;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotNull;


@JsonPropertyOrder(alphabetic = true)
public class MetaServiceConfig extends AbstractConfig implements HasDbConfig {

    private final MetaServiceDbConfig dbConfig;
    private final MetaValueConfig metaValueConfig;
    private final CacheConfig metaFeedCache;
    private final CacheConfig metaProcessorCache;
    private final CacheConfig metaTypeCache;
    private final Set<String> metaTypes;

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
        metaTypes = new HashSet<>(StreamTypeNames.ALL_TYPE_NAMES);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public MetaServiceConfig(@JsonProperty("db") final MetaServiceDbConfig dbConfig,
                             @JsonProperty("metaValue") final MetaValueConfig metaValueConfig,
                             @JsonProperty("metaFeedCache") final CacheConfig metaFeedCache,
                             @JsonProperty("metaProcessorCache") final CacheConfig metaProcessorCache,
                             @JsonProperty("metaTypeCache") final CacheConfig metaTypeCache,
                             @JsonProperty("metaTypes") final Set<String> metaTypes) {
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

    @RequiresRestart(RestartScope.SYSTEM)
    @NotEmpty
    @NotNull
    @IsSupersetOf(allowedValues = StreamTypeNames.ALL_TYPE_NAMES.stream().toArray())
    @JsonPropertyDescription("Set of supported meta type names. This set must contain all of the names " +
            "in the default value for this property but can contain additional names.")
    public Set<String> getMetaTypes() {
        return metaTypes;
    }

    // See TODO comment at top of StreamTypeNames regarding why we have this
    @JsonIgnore
    @ValidationMethod(message = "metaTypes must contain at least all of the names in the default value for metaTypes. " +
            "All items must be non-null and not empty.")
    public boolean isMetaTypesSetValid() {
        return metaTypes != null
                && metaTypes.containsAll(StreamTypeNames.ALL_TYPE_NAMES)
                && metaTypes.stream()
                .allMatch(type ->
                        type != null && !type.trim().isEmpty());
    }

    public MetaServiceConfig withMetaValueConfig(final MetaValueConfig metaValueConfig) {
        return new MetaServiceConfig(
                dbConfig,
                metaValueConfig,
                metaFeedCache,
                metaProcessorCache,
                metaTypeCache,
                metaTypes);
    }

    @BootStrapConfig
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
