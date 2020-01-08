package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.statistics.impl.InternalStatisticsConfig;
import stroom.statistics.impl.hbase.internal.HBaseStatisticsConfig;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class StatisticsConfig implements IsConfig {

    public static final String PROP_NAME_SQL = "sql";
    public static final String PROP_NAME_HBASE = "hbase";
    public static final String PROP_NAME_INTERNAL = "internal";

    private SQLStatisticsConfig sqlStatisticsConfig = new SQLStatisticsConfig();
    private HBaseStatisticsConfig hbaseStatisticsConfig = new HBaseStatisticsConfig();
    private InternalStatisticsConfig internalStatisticsConfig = new InternalStatisticsConfig();

    @JsonProperty(PROP_NAME_SQL)
    public SQLStatisticsConfig getSqlStatisticsConfig() {
        return sqlStatisticsConfig;
    }

    @SuppressWarnings("unused")
    public void setSqlStatisticsConfig(final SQLStatisticsConfig sqlStatisticsConfig) {
        this.sqlStatisticsConfig = sqlStatisticsConfig;
    }

    @JsonProperty(PROP_NAME_HBASE)
    public HBaseStatisticsConfig getHbaseStatisticsConfig() {
        return hbaseStatisticsConfig;
    }

    @SuppressWarnings("unused")
    public void setHbaseStatisticsConfig(final HBaseStatisticsConfig hbaseStatisticsConfig) {
        this.hbaseStatisticsConfig = hbaseStatisticsConfig;
    }

    @JsonProperty(PROP_NAME_INTERNAL)
    public InternalStatisticsConfig getInternalStatisticsConfig() {
        return internalStatisticsConfig;
    }

    @SuppressWarnings("unused")
    public void setInternalStatisticsConfig(final InternalStatisticsConfig internalStatisticsConfig) {
        this.internalStatisticsConfig = internalStatisticsConfig;
    }
}
