package stroom.analytics.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.query.common.v2.AnalyticResultStoreConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@SuppressWarnings("unused")
@JsonPropertyOrder(alphabetic = true)
public class AnalyticsConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final AnalyticsDbConfig dbConfig;
    @JsonPropertyDescription("Name of timezone (ZoneId) that will be used during alert generation.")
    private final String timezone;
    @JsonPropertyDescription("Configuration for the data store used for analytics.")
    private final AnalyticResultStoreConfig resultStoreConfig;

    public AnalyticsConfig() {
        dbConfig = new AnalyticsDbConfig();
        timezone = "UTC";
        resultStoreConfig = new AnalyticResultStoreConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AnalyticsConfig(@JsonProperty("db") final AnalyticsDbConfig dbConfig,
                           @JsonProperty("timezone") final String timezone,
                           @JsonProperty("resultStore") final AnalyticResultStoreConfig resultStoreConfig) {
        this.dbConfig = dbConfig;
        this.timezone = timezone;
        this.resultStoreConfig = resultStoreConfig;
    }

    @Override
    @JsonProperty("db")
    public AnalyticsDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonProperty
    public String getTimezone() {
        return timezone;
    }

    @JsonProperty("resultStore")
    public AnalyticResultStoreConfig getResultStoreConfig() {
        return resultStoreConfig;
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
