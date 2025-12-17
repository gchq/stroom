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

package stroom.config.app;

import stroom.statistics.impl.InternalStatisticsConfig;
import stroom.statistics.impl.hbase.internal.HBaseStatisticsConfig;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class StatisticsConfig extends AbstractConfig implements IsStroomConfig {

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
