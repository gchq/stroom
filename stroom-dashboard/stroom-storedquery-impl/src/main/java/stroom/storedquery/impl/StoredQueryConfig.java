package stroom.storedquery.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.Min;

@JsonPropertyOrder(alphabetic = true)
public class StoredQueryConfig extends AbstractConfig implements HasDbConfig {

    private final int itemsRetention;
    private final int daysRetention;
    private final StoredQueryDbConfig dbConfig;

    public StoredQueryConfig() {
        itemsRetention = 100;
        daysRetention = 365;
        dbConfig = new StoredQueryDbConfig();
    }

    @JsonCreator
    public StoredQueryConfig(@JsonProperty("itemsRetention") final int itemsRetention,
                             @JsonProperty("daysRetention") final int daysRetention,
                             @JsonProperty("db") final StoredQueryDbConfig dbConfig) {
        this.itemsRetention = itemsRetention;
        this.daysRetention = daysRetention;
        this.dbConfig = dbConfig;
    }

    @JsonPropertyDescription("The maximum number of query history items that will be retained")
    public int getItemsRetention() {
        return itemsRetention;
    }

    @Min(0)
    @JsonPropertyDescription("The number of days query history items will be retained for")
    public int getDaysRetention() {
        return daysRetention;
    }

    @Override
    @JsonProperty("db")
    public StoredQueryDbConfig getDbConfig() {
        return dbConfig;
    }

    @BootStrapConfig
    public static class StoredQueryDbConfig extends AbstractDbConfig {

        public StoredQueryDbConfig() {
            super();
        }

        @JsonCreator
        public StoredQueryDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
