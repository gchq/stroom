/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.meta.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.shared.DataFormatNames;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.validation.AllMatchPattern;
import stroom.util.shared.validation.IsSupersetOf;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;


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
    private final Set<String> dataFormats;
    private final int metaStatusUpdateBatchSize;

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
        dataFormats = new HashSet<>(DataFormatNames.ALL_HARD_CODED_FORMAT_NAMES);
        metaStatusUpdateBatchSize = 0;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public MetaServiceConfig(@JsonProperty("db") final MetaServiceDbConfig dbConfig,
                             @JsonProperty("metaValue") final MetaValueConfig metaValueConfig,
                             @JsonProperty("metaFeedCache") final CacheConfig metaFeedCache,
                             @JsonProperty("metaProcessorCache") final CacheConfig metaProcessorCache,
                             @JsonProperty("metaTypeCache") final CacheConfig metaTypeCache,
                             @JsonProperty("metaTypes") final Set<String> metaTypes,
                             @JsonProperty("rawMetaTypes") final Set<String> rawMetaTypes,
                             @JsonProperty("dataFormats") final Set<String> dataFormats,
                             @JsonProperty("metaStatusUpdateBatchSize") final int metaStatusUpdateBatchSize) {
        this.dbConfig = dbConfig;
        this.metaValueConfig = metaValueConfig;
        this.metaFeedCache = metaFeedCache;
        this.metaProcessorCache = metaProcessorCache;
        this.metaTypeCache = metaTypeCache;
        this.metaTypes = metaTypes;
        this.rawMetaTypes = rawMetaTypes;
        this.dataFormats = dataFormats;
        this.metaStatusUpdateBatchSize = metaStatusUpdateBatchSize;
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
    @JsonPropertyDescription(
            "Set of meta type names that are used for received raw data. " +
            "Types defined here will be read using the Data Encoding set on the Feed's settings." +
            "Any custom types added to the 'metaTypes' property that are used for raw data must also be " +
            "added here." +
            "This set must contain all of the names in the default value for this property but can " +
            "contain additional names.")
    public Set<String> getRawMetaTypes() {
        return rawMetaTypes;
    }

    @NotNull
    @Size(min = 1)
    @AllMatchPattern(pattern = "^[A-Z][A-Z_]+$")
    @IsSupersetOf(requiredValues = {
            DataFormatNames.XML,
            DataFormatNames.XML_FRAGMENT,
            DataFormatNames.JSON,
            DataFormatNames.YAML,
            DataFormatNames.TOML,
            DataFormatNames.INI,
            DataFormatNames.CSV,
            DataFormatNames.CSV_NO_HEADER,
            DataFormatNames.TSV,
            DataFormatNames.TSV_NO_HEADER,
            DataFormatNames.PSV,
            DataFormatNames.PSV_NO_HEADER,
            DataFormatNames.FIXED_WIDTH,
            DataFormatNames.FIXED_WIDTH_NO_HEADER,
            DataFormatNames.TEXT,
            DataFormatNames.SYSLOG,
    }) // List must match stroom.meta.shared.DataFormatNames#ALL_HARD_CODED_FORMAT_NAMES
    @JsonPropertyDescription(
            "Set of data format names. " +
            "This set must contain all of the names in the default value for this property but can " +
            "contain additional names.")
    public Set<String> getDataFormats() {
        return dataFormats;
    }

    @Min(0)
    @JsonPropertyDescription(
            "The number of streams to delete/restore using a filter in a single " +
            "batch. Each batch will run in a separate transaction. A value of zero means a single " +
            "batch will be used.")
    public int getMetaStatusUpdateBatchSize() {
        return metaStatusUpdateBatchSize;
    }

    public MetaServiceConfig withMetaValueConfig(final MetaValueConfig metaValueConfig) {
        return new MetaServiceConfig(
                dbConfig,
                metaValueConfig,
                metaFeedCache,
                metaProcessorCache,
                metaTypeCache,
                metaTypes,
                rawMetaTypes,
                dataFormats,
                metaStatusUpdateBatchSize);
    }

    public MetaServiceConfig withMetaStatusUpdateBatchSize(
            final int metaStatusUpdateBatchSize) {

        return new MetaServiceConfig(
                dbConfig,
                metaValueConfig,
                metaFeedCache,
                metaProcessorCache,
                metaTypeCache,
                metaTypes,
                rawMetaTypes,
                dataFormats,
                metaStatusUpdateBatchSize);
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
               ", dataFormats=" + dataFormats +
               ", metaStatusUpdateBatchSize=" + metaStatusUpdateBatchSize +
               '}';
    }

    @SuppressWarnings("unused") // Used by jakarta.validation
    @JsonIgnore
    @ValidationMethod(message = "The 'rawMetaTypes' property must be a sub-set of the 'metaTypes' property.")
    @Valid
    // Seems to be ignored if not prefixed with 'is'
    public boolean isValidRawTypesSet() {
        LOGGER.debug("metaTypes: {}, rawMetaTypes: {}", metaTypes, rawMetaTypes);
        return metaTypes != null
               && metaTypes.containsAll(NullSafe.set(rawMetaTypes));
    }


    // --------------------------------------------------------------------------------


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
