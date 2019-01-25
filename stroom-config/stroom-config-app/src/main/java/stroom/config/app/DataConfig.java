package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.meta.impl.db.MetaServiceConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DataConfig implements IsConfig {
    private MetaServiceConfig dataMetaServiceConfig = new MetaServiceConfig();
    private DataStoreServiceConfig dataStoreServiceConfig = new DataStoreServiceConfig();

    public DataConfig() {
    }

    @Inject
    DataConfig(final MetaServiceConfig dataMetaServiceConfig,
               final DataStoreServiceConfig dataStoreServiceConfig) {
        this.dataMetaServiceConfig = dataMetaServiceConfig;
        this.dataStoreServiceConfig = dataStoreServiceConfig;
    }

    @JsonProperty("meta")
    public MetaServiceConfig getDataMetaServiceConfig() {
        return dataMetaServiceConfig;
    }

    public void setDataMetaServiceConfig(final MetaServiceConfig dataMetaServiceConfig) {
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
