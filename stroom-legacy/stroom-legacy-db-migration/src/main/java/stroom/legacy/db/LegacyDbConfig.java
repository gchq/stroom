package stroom.legacy.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
@Deprecated
public class LegacyDbConfig extends AbstractConfig implements HasDbConfig {
    private DbConfig dbConfig = new DbConfig();

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }
}
