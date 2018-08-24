package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.data.meta.impl.db.DataMetaServiceConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DataConfig {
    private DataMetaServiceConfig dataMetaServiceConfig = new DataMetaServiceConfig();
    private DataStoreServiceConfig dataStoreServiceConfig = new DataStoreServiceConfig();

    public DataConfig() {
    }

    @Inject
    DataConfig(final DataMetaServiceConfig dataMetaServiceConfig,
               final DataStoreServiceConfig dataStoreServiceConfig) {
        this.dataMetaServiceConfig = dataMetaServiceConfig;
        this.dataStoreServiceConfig = dataStoreServiceConfig;
    }

    @JsonProperty("meta")
    public DataMetaServiceConfig getDataMetaServiceConfig() {
        return dataMetaServiceConfig;
    }

    public void setDataMetaServiceConfig(final DataMetaServiceConfig dataMetaServiceConfig) {
        this.dataMetaServiceConfig = dataMetaServiceConfig;
    }

    @JsonProperty("store")
    public DataStoreServiceConfig getDataStoreServiceConfig() {
        return dataStoreServiceConfig;
    }

    public void setDataStoreServiceConfig(final DataStoreServiceConfig dataStoreServiceConfig) {
        this.dataStoreServiceConfig = dataStoreServiceConfig;
    }
}
