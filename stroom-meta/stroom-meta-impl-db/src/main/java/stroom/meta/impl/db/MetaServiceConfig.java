package stroom.meta.impl.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class MetaServiceConfig implements IsConfig, HasDbConfig {

    private DbConfig dbConfig;
    private MetaValueConfig metaValueConfig;

    public MetaServiceConfig() {
        this.dbConfig = new DbConfig();
        this.metaValueConfig = new MetaValueConfig();
    }

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public MetaValueConfig getMetaValueConfig() {
        return metaValueConfig;
    }

    public void setMetaValueConfig(final MetaValueConfig metaValueConfig) {
        this.metaValueConfig = metaValueConfig;
    }
}
