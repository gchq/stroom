/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.docstore.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class DocStoreConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final DocStoreDbConfig dbConfig;
    private final CacheConfig docRefInfoCache;
    private final CacheConfig docRefNameCache;
    private final StroomDuration physicalDeleteAge;

    public DocStoreConfig() {
        dbConfig = new DocStoreDbConfig();
        docRefInfoCache = CacheConfig.builder()
                .maximumSize(1000000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        docRefNameCache = CacheConfig.builder()
                .maximumSize(1000000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        physicalDeleteAge = StroomDuration.ofDays(30);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public DocStoreConfig(@JsonProperty("db") final DocStoreDbConfig dbConfig,
                          @JsonProperty("docRefInfoCache") final CacheConfig docRefInfoCache,
                          @JsonProperty("docRefNameCache") final CacheConfig docRefNameCache,
                          @JsonProperty("physicalDeleteAge") final StroomDuration physicalDeleteAge) {
        this.dbConfig = dbConfig;
        this.docRefInfoCache = docRefInfoCache;
        this.docRefNameCache = docRefNameCache;
        this.physicalDeleteAge = physicalDeleteAge;
    }

    @Override
    @JsonProperty("db")
    public DocStoreDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonProperty("docRefInfoCache")
    public CacheConfig getDocRefInfoCache() {
        return docRefInfoCache;
    }

    @JsonProperty("docRefNameCache")
    public CacheConfig getDocRefNameCache() {
        return docRefNameCache;
    }

    @JsonPropertyDescription("How long to keep logically deleted documents before physically deleting them " +
                             "and all associated data, audit, and snapshot rows. " +
                             "In ISO-8601 duration format, e.g. 'P30D' for 30 days.")
    public StroomDuration getPhysicalDeleteAge() {
        return physicalDeleteAge;
    }


    @BootStrapConfig
    public static class DocStoreDbConfig extends AbstractDbConfig {

        public DocStoreDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public DocStoreDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
