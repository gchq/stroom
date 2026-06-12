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

package stroom.index.impl;

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
public class IndexConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final IndexDbConfig dbConfig;
    private final int ramBufferSizeMB;
    private final IndexWriterConfig indexWriterConfig;
    private final CacheConfig indexCache;
    private final CacheConfig indexFieldCache;

    public IndexConfig() {
        dbConfig = new IndexDbConfig();
        ramBufferSizeMB = 1024;
        indexWriterConfig = new IndexWriterConfig();
        indexCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        indexFieldCache = CacheConfig.builder()
                .maximumSize(10000L)
                .expireAfterWrite(StroomDuration.ofHours(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public IndexConfig(@JsonProperty("db") final IndexDbConfig dbConfig,
                       @JsonProperty("ramBufferSizeMB") final int ramBufferSizeMB,
                       @JsonProperty("writer") final IndexWriterConfig indexWriterConfig,
                       @JsonProperty("indexCache") final CacheConfig indexCache,
                       @JsonProperty("indexFieldCache") final CacheConfig indexFieldCache) {
        this.dbConfig = dbConfig;
        this.ramBufferSizeMB = ramBufferSizeMB;
        this.indexWriterConfig = indexWriterConfig;
        this.indexCache = indexCache;
        this.indexFieldCache = indexFieldCache;
    }

    @Override
    @JsonProperty("db")
    public IndexDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonPropertyDescription("The amount of RAM Lucene can use to buffer when indexing in Mb")
    public int getRamBufferSizeMB() {
        return ramBufferSizeMB;
    }

    @JsonProperty("writer")
    public IndexWriterConfig getIndexWriterConfig() {
        return indexWriterConfig;
    }

    public CacheConfig getIndexCache() {
        return indexCache;
    }

    public CacheConfig getIndexFieldCache() {
        return indexFieldCache;
    }

    @Override
    public String toString() {
        return "IndexConfig{" +
                "dbConfig=" + dbConfig +
                ", ramBufferSizeMB=" + ramBufferSizeMB +
                ", indexWriterConfig=" + indexWriterConfig +
                ", indexStructureCache=" + indexFieldCache +
                '}';
    }

    @BootStrapConfig
    public static class IndexDbConfig extends AbstractDbConfig {

        public IndexDbConfig() {
            super();
        }

        @JsonCreator
        public IndexDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
