package stroom.statistics.sql;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.statistics.sql.search.SearchConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SQLStatisticsConfig {
    private String docRefType = "StatisticStore";
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private ConnectionPoolConfig connectionPoolConfig = new ConnectionPoolConfig();
    private SearchConfig searchConfig;
    private int statisticAggregationBatchSize = 1000000;
    private String maxProcessingAge;

    public SQLStatisticsConfig() {
        searchConfig = new SearchConfig();
    }

    @Inject
    public SQLStatisticsConfig(final SearchConfig searchConfig) {
        this.searchConfig = searchConfig;
    }

    @JsonPropertyDescription("The entity type for the sql statistics service")
    public String getDocRefType() {
        return docRefType;
    }

    public void setDocRefType(final String docRefType) {
        this.docRefType = docRefType;
    }

    @JsonProperty("connection")
    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(final ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    @JsonProperty("connectionPool")
    public ConnectionPoolConfig getConnectionPoolConfig() {
        return connectionPoolConfig;
    }

    public void setConnectionPoolConfig(final ConnectionPoolConfig connectionPoolConfig) {
        this.connectionPoolConfig = connectionPoolConfig;
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

    @JsonPropertyDescription("The maximum age (e.g. '90d') of statistics to process and retain, i.e. any statistics with an statistic event time older than the current time minus maxProcessingAge will be silently dropped.  Existing statistic data over this age will be purged during statistic aggregation. Leave blank to process/retain all data.")
    public String getMaxProcessingAge() {
        return maxProcessingAge;
    }

    public void setMaxProcessingAge(final String maxProcessingAge) {
        this.maxProcessingAge = maxProcessingAge;
    }
}
