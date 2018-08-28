package stroom.statistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.statistics.internal.InternalStatisticsConfig;
import stroom.statistics.sql.SQLStatisticsConfig;
import stroom.statistics.stroomstats.internal.HBaseStatisticsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StatisticsConfig {
    private SQLStatisticsConfig sqlStatisticsConfig;
    private HBaseStatisticsConfig hbaseStatisticsConfig;
    private InternalStatisticsConfig internalStatisticsConfig;

    public StatisticsConfig() {
        this.sqlStatisticsConfig = new SQLStatisticsConfig();
        this.hbaseStatisticsConfig = new HBaseStatisticsConfig();
        this.internalStatisticsConfig = new InternalStatisticsConfig();
    }

    @Inject
    public StatisticsConfig(final SQLStatisticsConfig sqlStatisticsConfig,
                            final HBaseStatisticsConfig hbaseStatisticsConfig,
                            final InternalStatisticsConfig internalStatisticsConfig) {
        this.sqlStatisticsConfig = sqlStatisticsConfig;
        this.hbaseStatisticsConfig = hbaseStatisticsConfig;
        this.internalStatisticsConfig = internalStatisticsConfig;
    }

    @JsonProperty("sql")
    public SQLStatisticsConfig getSqlStatisticsConfig() {
        return sqlStatisticsConfig;
    }

    public void setSqlStatisticsConfig(final SQLStatisticsConfig sqlStatisticsConfig) {
        this.sqlStatisticsConfig = sqlStatisticsConfig;
    }

    @JsonProperty("hbase")
    public HBaseStatisticsConfig getHbaseStatisticsConfig() {
        return hbaseStatisticsConfig;
    }

    public void setHbaseStatisticsConfig(final HBaseStatisticsConfig hbaseStatisticsConfig) {
        this.hbaseStatisticsConfig = hbaseStatisticsConfig;
    }

    @JsonProperty("internal")
    public InternalStatisticsConfig getInternalStatisticsConfig() {
        return internalStatisticsConfig;
    }

    public void setInternalStatisticsConfig(final InternalStatisticsConfig internalStatisticsConfig) {
        this.internalStatisticsConfig = internalStatisticsConfig;
    }
}
