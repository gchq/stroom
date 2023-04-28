package stroom.analytics.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@SuppressWarnings("unused")
@JsonPropertyOrder(alphabetic = true)
public class AnalyticsConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final AnalyticsDbConfig dbConfig;

    public AnalyticsConfig() {
        dbConfig = new AnalyticsDbConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AnalyticsConfig(@JsonProperty("db") final AnalyticsDbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    @JsonProperty("db")
    public AnalyticsDbConfig getDbConfig() {
        return dbConfig;
    }

    @BootStrapConfig
    public static class AnalyticsDbConfig extends AbstractDbConfig implements IsStroomConfig {

        public AnalyticsDbConfig() {
            super();
        }

        @JsonCreator
        public AnalyticsDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
