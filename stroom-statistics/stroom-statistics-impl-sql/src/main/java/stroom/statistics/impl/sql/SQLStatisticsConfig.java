package stroom.statistics.impl.sql;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.statistics.impl.sql.search.SearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.checkerframework.checker.nullness.qual.Nullable;

@JsonPropertyOrder(alphabetic = true)
public class SQLStatisticsConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final SQLStatisticsDbConfig dbConfig;
    private final String docRefType;
    private final SearchConfig searchConfig;
    private final int statisticAggregationBatchSize;
    // TODO 29/11/2021 AT: Make final
    private StroomDuration maxProcessingAge;
    private final CacheConfig dataSourceCache;

    public SQLStatisticsConfig() {
        dbConfig = new SQLStatisticsDbConfig();
        docRefType = "StatisticStore";
        searchConfig = new SearchConfig();
        statisticAggregationBatchSize = 1000000;
        maxProcessingAge = null;
        dataSourceCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SQLStatisticsConfig(@JsonProperty("db") final SQLStatisticsDbConfig dbConfig,
                               @JsonProperty("docRefType") final String docRefType,
                               @JsonProperty("search") final SearchConfig searchConfig,
                               @JsonProperty("statisticAggregationBatchSize") final int statisticAggregationBatchSize,
                               @JsonProperty("maxProcessingAge") final StroomDuration maxProcessingAge,
                               @JsonProperty("dataSourceCache") final CacheConfig dataSourceCache) {
        this.dbConfig = dbConfig;
        this.docRefType = docRefType;
        this.searchConfig = searchConfig;
        this.statisticAggregationBatchSize = statisticAggregationBatchSize;
        this.maxProcessingAge = maxProcessingAge;
        this.dataSourceCache = dataSourceCache;
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

    public SQLStatisticsConfig withMaxProcessingAge(final StroomDuration maxProcessingAge) {
        return new SQLStatisticsConfig(
                dbConfig,
                docRefType,
                searchConfig,
                statisticAggregationBatchSize,
                maxProcessingAge,
                dataSourceCache);
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
