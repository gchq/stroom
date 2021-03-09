package stroom.cluster.lock.impl.db;

import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class ClusterLockConfig extends AbstractConfig implements HasDbConfig {

    private DbConfig dbConfig = new DbConfig();

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }
}
