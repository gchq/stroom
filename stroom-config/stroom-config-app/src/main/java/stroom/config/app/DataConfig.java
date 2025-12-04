/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.config.app;

import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.meta.impl.MetaServiceConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class DataConfig extends AbstractConfig implements IsStroomConfig {

    public static final String NAME = "data";

    public static final String PROP_NAME_META = "meta";
    public static final String PROP_NAME_RETENTION = "retention";
    public static final String PROP_NAME_STORE = "store";
    public static final String PROP_NAME_FILESYSTEM_VOLUME = "filesystemVolume";

    private final MetaServiceConfig metaServiceConfig;
    private final DataRetentionConfig dataRetentionConfig;
    private final DataStoreServiceConfig dataStoreServiceConfig;
    private final FsVolumeConfig fsVolumeConfig;

    public DataConfig() {
        metaServiceConfig = new MetaServiceConfig();
        dataRetentionConfig = new DataRetentionConfig();
        dataStoreServiceConfig = new DataStoreServiceConfig();
        fsVolumeConfig = new FsVolumeConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public DataConfig(@JsonProperty(PROP_NAME_META) final MetaServiceConfig metaServiceConfig,
                      @JsonProperty(PROP_NAME_RETENTION) final DataRetentionConfig dataRetentionConfig,
                      @JsonProperty(PROP_NAME_STORE) final DataStoreServiceConfig dataStoreServiceConfig,
                      @JsonProperty(PROP_NAME_FILESYSTEM_VOLUME) final FsVolumeConfig fsVolumeConfig) {
        this.metaServiceConfig = metaServiceConfig;
        this.dataRetentionConfig = dataRetentionConfig;
        this.dataStoreServiceConfig = dataStoreServiceConfig;
        this.fsVolumeConfig = fsVolumeConfig;
    }

    @JsonProperty(PROP_NAME_META)
    public MetaServiceConfig getMetaServiceConfig() {
        return metaServiceConfig;
    }

    @JsonProperty(PROP_NAME_RETENTION)
    public DataRetentionConfig getDataRetentionConfig() {
        return dataRetentionConfig;
    }

    @JsonProperty(PROP_NAME_STORE)
    public DataStoreServiceConfig getDataStoreServiceConfig() {
        return dataStoreServiceConfig;
    }

    @JsonProperty(PROP_NAME_FILESYSTEM_VOLUME)
    public FsVolumeConfig getFsVolumeConfig() {
        return fsVolumeConfig;
    }
}
