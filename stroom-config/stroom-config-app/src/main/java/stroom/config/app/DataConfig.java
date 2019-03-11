package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.data.retention.impl.DataRetentionConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.meta.impl.db.MetaServiceConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DataConfig implements IsConfig {
    private MetaServiceConfig dataMetaServiceConfig = new MetaServiceConfig();
    private DataRetentionConfig dataRetentionConfig = new DataRetentionConfig();
    private DataStoreServiceConfig dataStoreServiceConfig = new DataStoreServiceConfig();

    public DataConfig() {
    }

    @Inject
    DataConfig(final MetaServiceConfig dataMetaServiceConfig,
               final DataRetentionConfig dataRetentionConfig,
               final DataStoreServiceConfig dataStoreServiceConfig) {
        this.dataMetaServiceConfig = dataMetaServiceConfig;
        this.dataRetentionConfig = dataRetentionConfig;
        this.dataStoreServiceConfig = dataStoreServiceConfig;
    }

    @JsonProperty("meta")
    public MetaServiceConfig getDataMetaServiceConfig() {
        return dataMetaServiceConfig;
    }

    public void setDataMetaServiceConfig(final MetaServiceConfig dataMetaServiceConfig) {
        this.dataMetaServiceConfig = dataMetaServiceConfig;
    }

    @JsonProperty("retention")
    public DataRetentionConfig getDataRetentionConfig() {
        return dataRetentionConfig;
    }

    public void setDataRetentionConfig(final DataRetentionConfig dataRetentionConfig) {
        this.dataRetentionConfig = dataRetentionConfig;
    }

    @JsonProperty("store")
    public DataStoreServiceConfig getDataStoreServiceConfig() {
        return dataStoreServiceConfig;
    }

    public void setDataStoreServiceConfig(final DataStoreServiceConfig dataStoreServiceConfig) {
        this.dataStoreServiceConfig = dataStoreServiceConfig;
    }
}
