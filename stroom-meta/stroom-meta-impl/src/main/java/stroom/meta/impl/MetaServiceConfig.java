package stroom.meta.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.data.shared.StreamTypeNames;
import stroom.util.NullSafe;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.IsSupersetOf;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import java.util.HashSet;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;


@JsonPropertyOrder(alphabetic = true)
public class MetaServiceConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaServiceConfig.class);

    private final MetaServiceDbConfig dbConfig;
    private final MetaValueConfig metaValueConfig;
    private final CacheConfig metaFeedCache;
    private final CacheConfig metaProcessorCache;
    private final CacheConfig metaTypeCache;
    private final Set<String> metaTypes;
    private final Set<String> rawMetaTypes;

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
        metaTypes = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_STREAM_TYPE_NAMES);
        rawMetaTypes = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_RAW_STREAM_TYPE_NAMES);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public MetaServiceConfig(@JsonProperty("db") final MetaServiceDbConfig dbConfig,
                             @JsonProperty("metaValue") final MetaValueConfig metaValueConfig,
                             @JsonProperty("metaFeedCache") final CacheConfig metaFeedCache,
                             @JsonProperty("metaProcessorCache") final CacheConfig metaProcessorCache,
                             @JsonProperty("metaTypeCache") final CacheConfig metaTypeCache,
                             @JsonProperty("metaTypes") final Set<String> metaTypes,
                             @JsonProperty("rawMetaTypes") final Set<String> rawMetaTypes) {
        this.dbConfig = dbConfig;
        this.metaValueConfig = metaValueConfig;
        this.metaFeedCache = metaFeedCache;
        this.metaProcessorCache = metaProcessorCache;
        this.metaTypeCache = metaTypeCache;
        this.metaTypes = metaTypes;
        this.rawMetaTypes = rawMetaTypes;
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
    @NotNull
    @Size(min = 1)
    @IsSupersetOf(requiredValues = {
            StreamTypeNames.RAW_EVENTS,
            StreamTypeNames.RAW_REFERENCE,
            StreamTypeNames.EVENTS,
            StreamTypeNames.REFERENCE,
            StreamTypeNames.META,
            StreamTypeNames.ERROR,
            StreamTypeNames.CONTEXT,
    }) // List should contain as a minimum all those types that the java code reference
    @JsonPropertyDescription("Set of supported meta type names. This set must contain all of the names " +
            "in the default value for this property but can contain additional names. " +
            "Any custom types added here that are used for raw data must also be " +
            "added to the property 'rawMetaTypes'." +
            "")
    public Set<String> getMetaTypes() {
        return metaTypes;
    }

    @NotNull
    @Size(min = 1)
    @IsSupersetOf(requiredValues = {
            StreamTypeNames.RAW_EVENTS,
            StreamTypeNames.RAW_REFERENCE,
    }) // List must match stroom.data.shared.StreamTypeNames#ALL_HARD_CODED_RAW_STREAM_TYPE_NAMES
    @JsonPropertyDescription("Set of meta type names that are used for received raw data. " +
            "Types defined here will be read using the Data Encoding set on the Feed's settings." +
            "Any custom types added to the 'metaTypes' property that are used for raw data must also be " +
            "added here." +
            "This set must contain all of the names in the default value for this property but can " +
            "contain additional names.")
    public Set<String> getRawMetaTypes() {
        return rawMetaTypes;
    }

    public MetaServiceConfig withMetaValueConfig(final MetaValueConfig metaValueConfig) {
        return new MetaServiceConfig(
                dbConfig,
                metaValueConfig,
                metaFeedCache,
                metaProcessorCache,
                metaTypeCache,
                metaTypes,
                rawMetaTypes);
    }

    @Override
    public String toString() {
        return "MetaServiceConfig{" +
                "dbConfig=" + dbConfig +
                ", metaValueConfig=" + metaValueConfig +
                ", metaFeedCache=" + metaFeedCache +
                ", metaProcessorCache=" + metaProcessorCache +
                ", metaTypeCache=" + metaTypeCache +
                ", metaTypes=" + metaTypes +
                ", rawMetaTypes=" + rawMetaTypes +
                '}';
    }

    @SuppressWarnings("unused") // Used by javax.validation
    @JsonIgnore
    @ValidationMethod(message = "The 'rawMetaTypes' property must be a sub-set of the 'metaTypes' property.")
    @Valid
    // Seems to be ignored if not prefixed with 'is'
    public boolean isValidRawTypesSet() {
        LOGGER.debug("metaTypes: {}, rawMetaTypes: {}", metaTypes, rawMetaTypes);
        return metaTypes != null
                && metaTypes.containsAll(NullSafe.nonNullSet(rawMetaTypes));
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
