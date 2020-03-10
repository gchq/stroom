package stroom.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;

import javax.inject.Singleton;

@Singleton
public class AuthenticationDbConfig implements HasDbConfig {
    private DbConfig dbConfig = new DbConfig();

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }
}
