package stroom.storedquery.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class StoredQueryConfig implements IsConfig, HasDbConfig {

    private int itemsRetention = 100;
    private int daysRetention = 365;
    private DbConfig dbConfig;

    public StoredQueryConfig() {
        this.dbConfig = new DbConfig();
    }

    @JsonPropertyDescription("The maximum number of query history items that will be retained")
    public int getItemsRetention() {
        return itemsRetention;
    }

    public void setItemsRetention(final int itemsRetention) {
        this.itemsRetention = itemsRetention;
    }

    @JsonPropertyDescription("The number of days query history items will be retained for")
    public int getDaysRetention() {
        return daysRetention;
    }

    public void setDaysRetention(final int daysRetention) {
        this.daysRetention = daysRetention;
    }

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }
}
