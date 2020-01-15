package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.statistics.impl.InternalStatisticsConfig;
import stroom.statistics.impl.hbase.internal.HBaseStatisticsConfig;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class StatisticsConfig implements IsConfig {
    private SQLStatisticsConfig sqlStatisticsConfig = new SQLStatisticsConfig();
    private HBaseStatisticsConfig hbaseStatisticsConfig = new HBaseStatisticsConfig();
    private InternalStatisticsConfig internalStatisticsConfig = new InternalStatisticsConfig();

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
