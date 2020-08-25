package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.meta.impl.MetaServiceConfig;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
public class DataConfig extends AbstractConfig {
    public static final String NAME = "data";

    public static final String PROP_NAME_META = "meta";
    public static final String PROP_NAME_RETENTION = "retention";
    public static final String PROP_NAME_STORE = "store";
    public static final String PROP_NAME_FILESYSTEM_VOLUME = "filesystemVolume";

    private MetaServiceConfig metaServiceConfig = new MetaServiceConfig();
    private DataRetentionConfig dataRetentionConfig = new DataRetentionConfig();
    private DataStoreServiceConfig dataStoreServiceConfig = new DataStoreServiceConfig();
    private FsVolumeConfig fsVolumeConfig = new FsVolumeConfig();

    @JsonProperty(PROP_NAME_META)
    public MetaServiceConfig getMetaServiceConfig() {
        return metaServiceConfig;
    }

    @SuppressWarnings("unused")
    public void setMetaServiceConfig(final MetaServiceConfig metaServiceConfig) {
        this.metaServiceConfig = metaServiceConfig;
    }

    @JsonProperty(PROP_NAME_RETENTION)
    public DataRetentionConfig getDataRetentionConfig() {
        return dataRetentionConfig;
    }

    @SuppressWarnings("unused")
    public void setDataRetentionConfig(final DataRetentionConfig dataRetentionConfig) {
        this.dataRetentionConfig = dataRetentionConfig;
    }

    @JsonProperty(PROP_NAME_STORE)
    public DataStoreServiceConfig getDataStoreServiceConfig() {
        return dataStoreServiceConfig;
    }

    @SuppressWarnings("unused")
    public void setDataStoreServiceConfig(final DataStoreServiceConfig dataStoreServiceConfig) {
        this.dataStoreServiceConfig = dataStoreServiceConfig;
    }

    @JsonProperty(PROP_NAME_FILESYSTEM_VOLUME)
    public FsVolumeConfig getFsVolumeConfig() {
        return fsVolumeConfig;
    }

    @SuppressWarnings("unused")
    public void setFsVolumeConfig(final FsVolumeConfig fsVolumeConfig) {
        this.fsVolumeConfig = fsVolumeConfig;
    }
}
