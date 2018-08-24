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
    private HBaseStatisticsConfig hBaseStatisticsConfig;
    private InternalStatisticsConfig internalStatisticsConfig;

    public StatisticsConfig() {
        this.sqlStatisticsConfig = new SQLStatisticsConfig();
        this.hBaseStatisticsConfig = new HBaseStatisticsConfig();
        this.internalStatisticsConfig = new InternalStatisticsConfig();
    }

    @Inject
    public StatisticsConfig(final SQLStatisticsConfig sqlStatisticsConfig,
                            final HBaseStatisticsConfig hBaseStatisticsConfig,
                            final InternalStatisticsConfig internalStatisticsConfig) {
        this.sqlStatisticsConfig = sqlStatisticsConfig;
        this.hBaseStatisticsConfig = hBaseStatisticsConfig;
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
    public HBaseStatisticsConfig gethBaseStatisticsConfig() {
        return hBaseStatisticsConfig;
    }

    public void sethBaseStatisticsConfig(final HBaseStatisticsConfig hBaseStatisticsConfig) {
        this.hBaseStatisticsConfig = hBaseStatisticsConfig;
    }

    @JsonProperty("internal")
    public InternalStatisticsConfig getInternalStatisticsConfig() {
        return internalStatisticsConfig;
    }

    public void setInternalStatisticsConfig(final InternalStatisticsConfig internalStatisticsConfig) {
        this.internalStatisticsConfig = internalStatisticsConfig;
    }
}
