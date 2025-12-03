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

package stroom.statistics.impl.sql;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.statistics.impl.sql.search.SearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;

@JsonPropertyOrder(alphabetic = true)
public class SQLStatisticsConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final SQLStatisticsDbConfig dbConfig;
    private final String docRefType;
    private final SearchConfig searchConfig;
    private final int inMemAggregatorPoolSize;
    private final int inMemPooledAggregatorSizeThreshold;
    private final int inMemFinalAggregatorSizeThreshold;
    private final StroomDuration inMemPooledAggregatorAgeThreshold;
    private final int statisticFlushBatchSize;
    private final int statisticAggregationBatchSize;
    private final int statisticAggregationStageTwoBatchSize;
    // TODO 29/11/2021 AT: Make final
    private StroomDuration maxProcessingAge;
    private final CacheConfig dataSourceCache;
    private final StroomDuration slowQueryWarningThreshold;

    public SQLStatisticsConfig() {
        dbConfig = new SQLStatisticsDbConfig();
        docRefType = "StatisticStore";
        searchConfig = new SearchConfig();
        inMemAggregatorPoolSize = 10;
        inMemPooledAggregatorSizeThreshold = 1_000_000;
        inMemPooledAggregatorAgeThreshold = StroomDuration.ofMinutes(5);
        inMemFinalAggregatorSizeThreshold = 1_000_000;
        statisticFlushBatchSize = 8_000;
        statisticAggregationBatchSize = 1_000_000;
        statisticAggregationStageTwoBatchSize = 200_000;
        maxProcessingAge = null;
        dataSourceCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        slowQueryWarningThreshold = StroomDuration.ofSeconds(1);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SQLStatisticsConfig(
            @JsonProperty("db") final SQLStatisticsDbConfig dbConfig,
            @JsonProperty("docRefType") final String docRefType,
            @JsonProperty("search") final SearchConfig searchConfig,
            @JsonProperty("inMemAggregatorPoolSize") final int inMemAggregatorPoolSize,
            @JsonProperty("inMemPooledAggregatorSizeThreshold") final int inMemPooledAggregatorSizeThreshold,
            @JsonProperty("inMemPooledAggregatorAgeThreshold") final StroomDuration inMemPooledAggregatorAgeThreshold,
            @JsonProperty("inMemFinalAggregatorSizeThreshold") final int inMemFinalAggregatorSizeThreshold,
            @JsonProperty("statisticFlushBatchSize") final int statisticFlushBatchSize,
            @JsonProperty("statisticAggregationBatchSize") final int statisticAggregationBatchSize,
            @JsonProperty("statisticAggregationStageTwoBatchSize") final int statisticAggregationStageTwoBatchSize,
            @JsonProperty("maxProcessingAge") final StroomDuration maxProcessingAge,
            @JsonProperty("dataSourceCache") final CacheConfig dataSourceCache,
            @JsonProperty("slowQueryWarningThreshold") final StroomDuration slowQueryWarningThreshold) {

        this.dbConfig = dbConfig;
        this.docRefType = docRefType;
        this.searchConfig = searchConfig;
        this.inMemAggregatorPoolSize = inMemAggregatorPoolSize;
        this.inMemPooledAggregatorSizeThreshold = inMemPooledAggregatorSizeThreshold;
        this.inMemPooledAggregatorAgeThreshold = inMemPooledAggregatorAgeThreshold;
        this.inMemFinalAggregatorSizeThreshold = inMemFinalAggregatorSizeThreshold;
        this.statisticFlushBatchSize = statisticFlushBatchSize;
        this.statisticAggregationBatchSize = statisticAggregationBatchSize;
        this.statisticAggregationStageTwoBatchSize = statisticAggregationStageTwoBatchSize;
        this.maxProcessingAge = maxProcessingAge;
        this.dataSourceCache = dataSourceCache;
        this.slowQueryWarningThreshold = slowQueryWarningThreshold;
    }

    @Override
    @JsonProperty("db")
    public SQLStatisticsDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonPropertyDescription("The entity type for the sql statistics service")
    public String getDocRefType() {
        return docRefType;
    }

    @JsonProperty("search")
    public SearchConfig getSearchConfig() {
        return searchConfig;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @Min(1)
    @JsonPropertyDescription("Number of pooled in-memory aggregators in the pool." +
            "This pool of aggregators is the first stage of in-memory statistic aggregation.")
    public int getInMemAggregatorPoolSize() {
        return inMemAggregatorPoolSize;
    }

    @Min(1)
    @JsonPropertyDescription("Maximum size (number of entries) of each aggregator in the pool of in-memory " +
            "aggregator maps. " +
            "Once an aggregator has reached this size it will be merged into the final stage of in-memory " +
            "aggregation." +
            "This pool of aggregators is the first stage of in-memory statistic aggregation.")
    public int getInMemPooledAggregatorSizeThreshold() {
        return inMemPooledAggregatorSizeThreshold;
    }

    @JsonPropertyDescription("Maximum age of each aggregator in the pool of in-memory aggregator maps. " +
            "Once an aggregator has reached this age it will be merged into the final stage of in-memory aggregation." +
            "This pool of aggregators is the first stage of in-memory statistic aggregation.")
    public StroomDuration getInMemPooledAggregatorAgeThreshold() {
        return inMemPooledAggregatorAgeThreshold;
    }

    @Min(1)
    @JsonPropertyDescription("Maximum size (number of entries) of the final in-memory aggregator map." +
            "Once an aggregator has reached this size it will be flushed to the database." +
            "If statistic flush tasks are delaying shutdown of stroom you can reduce this value to make " +
            "the flushes smaller, at the cost of efficiency." +
            "This aggregator is the final stage of in-memory statistic aggregation.")
    public int getInMemFinalAggregatorSizeThreshold() {
        return inMemFinalAggregatorSizeThreshold;
    }

    @Min(1)
    @JsonPropertyDescription("Number of statistic events to write to SQL_STAT_VAL_SRC in one batch. " +
            "Sweet spot seems to be around 8-10k. Too high a number and there is a risk of the SQL statement " +
            "being too large for MySQL.")
    public int getStatisticFlushBatchSize() {
        return statisticFlushBatchSize;
    }

    @Min(1)
    @JsonPropertyDescription("Number of SQL_STAT_VAL_SRC records to merge into SQL_STAT_VAL in one batch" +
            "Typically a larger number is more efficient but means it will take longer which can delay a clean " +
            "shutdown of stroom. Higher numbers may also lead to database lock contention and lock wait errors.")
    public int getStatisticAggregationBatchSize() {
        return statisticAggregationBatchSize;
    }

    @Min(1)
    @JsonPropertyDescription("Number of SQL_STAT_VAL records to move to a coarser aggregation level in one batch. " +
            "Typically a larger number is more efficient but means it will take longer which can delay a clean " +
            "shutdown of stroom. Higher numbers may also lead to database lock contention and lock wait errors.")
    public int getStatisticAggregationStageTwoBatchSize() {
        return statisticAggregationStageTwoBatchSize;
    }

    @Nullable
    @JsonPropertyDescription("The maximum age of statistics to process and retain, i.e. any " +
            "statistics with an statistic event time older than the current time minus maxProcessingAge will " +
            "be silently dropped.  Existing statistic data over this age will be purged during statistic " +
            "aggregation. Set to null to process/retain all data.")
    public StroomDuration getMaxProcessingAge() {
        return maxProcessingAge;
    }

    @Deprecated(forRemoval = true) // Awaiting refactor to handle immutable config
    public void setMaxProcessingAge(final StroomDuration maxProcessingAge) {
        this.maxProcessingAge = maxProcessingAge;
    }

    public CacheConfig getDataSourceCache() {
        return dataSourceCache;
    }

    @JsonPropertyDescription("A warning will be logged for any statistics database queries that take longer than " +
            "this threshold to complete. A value of '0' or 'PT0' means no warnings will be logged at all.")
    public StroomDuration getSlowQueryWarningThreshold() {
        return slowQueryWarningThreshold;
    }

    public SQLStatisticsConfig withMaxProcessingAge(final StroomDuration maxProcessingAge) {
        return new SQLStatisticsConfig(
                dbConfig,
                docRefType,
                searchConfig,
                inMemAggregatorPoolSize,
                inMemPooledAggregatorSizeThreshold,
                inMemPooledAggregatorAgeThreshold,
                inMemFinalAggregatorSizeThreshold,
                statisticFlushBatchSize,
                statisticAggregationBatchSize,
                statisticAggregationStageTwoBatchSize,
                maxProcessingAge,
                dataSourceCache,
                slowQueryWarningThreshold);
    }

    public SQLStatisticsConfig withInMemAggregatorPoolSize(final int inMemAggregatorPoolSize) {
        return new SQLStatisticsConfig(
                dbConfig,
                docRefType,
                searchConfig,
                inMemAggregatorPoolSize,
                inMemPooledAggregatorSizeThreshold,
                inMemPooledAggregatorAgeThreshold,
                inMemFinalAggregatorSizeThreshold,
                statisticFlushBatchSize,
                statisticAggregationBatchSize,
                statisticAggregationStageTwoBatchSize,
                maxProcessingAge,
                dataSourceCache,
                slowQueryWarningThreshold);
    }

    public SQLStatisticsConfig withInMemPooledAggregatorSizeThreshold(
            final int inMemPooledAggregatorSizeThreshold) {

        return new SQLStatisticsConfig(
                dbConfig,
                docRefType,
                searchConfig,
                inMemAggregatorPoolSize,
                inMemPooledAggregatorSizeThreshold,
                inMemPooledAggregatorAgeThreshold,
                inMemFinalAggregatorSizeThreshold,
                statisticFlushBatchSize,
                statisticAggregationBatchSize,
                getStatisticAggregationStageTwoBatchSize(),
                maxProcessingAge,
                dataSourceCache,
                slowQueryWarningThreshold);
    }

    public SQLStatisticsConfig withInMemPooledAggregatorAgeThreshold(
            final StroomDuration inMemPooledAggregatorAgeThreshold) {

        return new SQLStatisticsConfig(
                dbConfig,
                docRefType,
                searchConfig,
                inMemAggregatorPoolSize,
                inMemPooledAggregatorSizeThreshold,
                inMemPooledAggregatorAgeThreshold,
                inMemFinalAggregatorSizeThreshold,
                statisticFlushBatchSize,
                statisticAggregationBatchSize,
                statisticAggregationStageTwoBatchSize,
                maxProcessingAge,
                dataSourceCache,
                slowQueryWarningThreshold);
    }

    public SQLStatisticsConfig withInMemFinalAggregatorSizeThreshold(
            final int inMemFinalAggregatorSizeThreshold) {

        return new SQLStatisticsConfig(
                dbConfig,
                docRefType,
                searchConfig,
                inMemAggregatorPoolSize,
                inMemPooledAggregatorSizeThreshold,
                inMemPooledAggregatorAgeThreshold,
                inMemFinalAggregatorSizeThreshold,
                statisticFlushBatchSize,
                statisticAggregationBatchSize,
                statisticAggregationStageTwoBatchSize,
                maxProcessingAge,
                dataSourceCache,
                slowQueryWarningThreshold);
    }

    @Override
    public String toString() {
        return "SQLStatisticsConfig{" +
                "dbConfig=" + dbConfig +
                ", docRefType='" + docRefType + '\'' +
                ", searchConfig=" + searchConfig +
                ", inMemAggregatorPoolSize=" + inMemAggregatorPoolSize +
                ", inMemPooledAggregatorSizeThreshold=" + inMemPooledAggregatorSizeThreshold +
                ", inMemFinalAggregatorSizeThreshold=" + inMemFinalAggregatorSizeThreshold +
                ", inMemPooledAggregatorAgeThreshold=" + inMemPooledAggregatorAgeThreshold +
                ", statisticFlushBatchSize=" + statisticFlushBatchSize +
                ", statisticAggregationBatchSize=" + statisticAggregationBatchSize +
                ", statisticAggregationStageTwoBatchSize=" + statisticAggregationStageTwoBatchSize +
                ", maxProcessingAge=" + maxProcessingAge +
                ", dataSourceCache=" + dataSourceCache +
                ", slowQueryWarningThreshold=" + slowQueryWarningThreshold +
                '}';
    }

    @BootStrapConfig
    public static class SQLStatisticsDbConfig extends AbstractDbConfig {

        public SQLStatisticsDbConfig() {
            super();
        }

        @JsonCreator
        public SQLStatisticsDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
