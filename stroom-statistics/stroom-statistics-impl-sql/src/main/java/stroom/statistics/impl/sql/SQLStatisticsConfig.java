package stroom.statistics.impl.sql;

import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.statistics.impl.sql.search.SearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.annotation.Nullable;
import javax.inject.Singleton;

@Singleton
public class SQLStatisticsConfig extends AbstractConfig implements HasDbConfig {
    private DbConfig dbConfig = new DbConfig();
    private String docRefType = "StatisticStore";
    private SearchConfig searchConfig = new SearchConfig();
    private int statisticAggregationBatchSize = 1000000;
    private StroomDuration maxProcessingAge;
    private CacheConfig dataSourceCache = new CacheConfig.Builder()
            .maximumSize(100L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @JsonPropertyDescription("The entity type for the sql statistics service")
    public String getDocRefType() {
        return docRefType;
    }

    public void setDocRefType(final String docRefType) {
        this.docRefType = docRefType;
    }

    @JsonProperty("search")
    public SearchConfig getSearchConfig() {
        return searchConfig;
    }

    public void setSearchConfig(final SearchConfig searchConfig) {
        this.searchConfig = searchConfig;
    }

    @JsonPropertyDescription("Number of SQL_STAT_VAL_SRC records to merge into SQL_STAT_VAL in one batch")
    public int getStatisticAggregationBatchSize() {
        return statisticAggregationBatchSize;
    }

    public void setStatisticAggregationBatchSize(final int statisticAggregationBatchSize) {
        this.statisticAggregationBatchSize = statisticAggregationBatchSize;
    }

    @Nullable
    @JsonPropertyDescription("The maximum age of statistics to process and retain, i.e. any " +
        "statistics with an statistic event time older than the current time minus maxProcessingAge will be silently " +
        "dropped.  Existing statistic data over this age will be purged during statistic aggregation. " +
        "Set to null to process/retain all data.")
    public StroomDuration getMaxProcessingAge() {
        return maxProcessingAge;
    }

    public void setMaxProcessingAge(final StroomDuration maxProcessingAge) {
        this.maxProcessingAge = maxProcessingAge;
    }

    public CacheConfig getDataSourceCache() {
        return dataSourceCache;
    }

    public void setDataSourceCache(final CacheConfig dataSourceCache) {
        this.dataSourceCache = dataSourceCache;
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
}
