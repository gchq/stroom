package stroom.statistics.impl.sql;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.statistics.impl.sql.search.SearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;

@JsonPropertyOrder(alphabetic = true)
public class SQLStatisticsConfig extends AbstractConfig implements HasDbConfig {

    private final SQLStatisticsDbConfig dbConfig;
    private final String docRefType;
    private final SearchConfig searchConfig;
    private final int statisticFlushBatchSize;
    private final int statisticAggregationBatchSize;
    // TODO 29/11/2021 AT: Make final
    private StroomDuration maxProcessingAge;
    private final CacheConfig dataSourceCache;
    private final StroomDuration slowQueryWarningThreshold;

    public SQLStatisticsConfig() {
        dbConfig = new SQLStatisticsDbConfig();
        docRefType = "StatisticStore";
        searchConfig = new SearchConfig();
        statisticFlushBatchSize = 8_000;
        statisticAggregationBatchSize = 1_000_000;
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
            @JsonProperty("statisticFlushBatchSize") final int statisticFlushBatchSize,
            @JsonProperty("statisticAggregationBatchSize") final int statisticAggregationBatchSize,
            @JsonProperty("maxProcessingAge") final StroomDuration maxProcessingAge,
            @JsonProperty("dataSourceCache") final CacheConfig dataSourceCache,
            @JsonProperty("slowQueryWarningThreshold") final StroomDuration slowQueryWarningThreshold) {

        this.dbConfig = dbConfig;
        this.docRefType = docRefType;
        this.searchConfig = searchConfig;
        this.statisticFlushBatchSize = statisticFlushBatchSize;
        this.statisticAggregationBatchSize = statisticAggregationBatchSize;
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

    @Min(1)
    @JsonPropertyDescription("Number of statistic events to write to SQL_STAT_VAL_SRC in one batch. " +
            "Sweet spot seems to be around 8-10k. Too high a number and there is a risk of the SQL statement " +
            "being too large for MySQL.")
    public int getStatisticFlushBatchSize() {
        return statisticFlushBatchSize;
    }

    @Min(1)
    @JsonPropertyDescription("Number of SQL_STAT_VAL_SRC records to merge into SQL_STAT_VAL in one batch")
    public int getStatisticAggregationBatchSize() {
        return statisticAggregationBatchSize;
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
                statisticFlushBatchSize,
                statisticAggregationBatchSize,
                maxProcessingAge,
                dataSourceCache, slowQueryWarningThreshold);
    }

    @Override
    public String toString() {
        return "SQLStatisticsConfig{" +
                "dbConfig=" + dbConfig +
                ", docRefType='" + docRefType + '\'' +
                ", searchConfig=" + searchConfig +
                ", statisticAggregationBatchSize=" + statisticAggregationBatchSize +
                ", maxProcessingAge='" + maxProcessingAge + '\'' +
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
