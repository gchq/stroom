package stroom.config.app;

import stroom.statistics.impl.InternalStatisticsConfig;
import stroom.statistics.impl.hbase.internal.HBaseStatisticsConfig;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class StatisticsConfig extends AbstractConfig {

    public static final String PROP_NAME_SQL = "sql";
    public static final String PROP_NAME_HBASE = "hbase";
    public static final String PROP_NAME_INTERNAL = "internal";

    private final SQLStatisticsConfig sqlStatisticsConfig;
    private final HBaseStatisticsConfig hbaseStatisticsConfig;
    private final InternalStatisticsConfig internalStatisticsConfig;

    public StatisticsConfig() {
        sqlStatisticsConfig = new SQLStatisticsConfig();
        hbaseStatisticsConfig = new HBaseStatisticsConfig();
        internalStatisticsConfig = new InternalStatisticsConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public StatisticsConfig(
            @JsonProperty(PROP_NAME_SQL) final SQLStatisticsConfig sqlStatisticsConfig,
            @JsonProperty(PROP_NAME_HBASE) final HBaseStatisticsConfig hbaseStatisticsConfig,
            @JsonProperty(PROP_NAME_INTERNAL) final InternalStatisticsConfig internalStatisticsConfig) {
        this.sqlStatisticsConfig = sqlStatisticsConfig;
        this.hbaseStatisticsConfig = hbaseStatisticsConfig;
        this.internalStatisticsConfig = internalStatisticsConfig;
    }

    @JsonProperty(PROP_NAME_SQL)
    public SQLStatisticsConfig getSqlStatisticsConfig() {
        return sqlStatisticsConfig;
    }

    @JsonProperty(PROP_NAME_HBASE)
    public HBaseStatisticsConfig getHbaseStatisticsConfig() {
        return hbaseStatisticsConfig;
    }

    @JsonProperty(PROP_NAME_INTERNAL)
    public InternalStatisticsConfig getInternalStatisticsConfig() {
        return internalStatisticsConfig;
    }

    @Override
    public String toString() {
        return "StatisticsConfig{" +
                "sqlStatisticsConfig=" + sqlStatisticsConfig +
                ", hbaseStatisticsConfig=" + hbaseStatisticsConfig +
                ", internalStatisticsConfig=" + internalStatisticsConfig +
                '}';
    }
}
