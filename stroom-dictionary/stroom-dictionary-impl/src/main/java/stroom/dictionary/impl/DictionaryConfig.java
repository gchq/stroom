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

package stroom.dictionary.impl;

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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class DictionaryConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final DictionaryDbConfig dbConfig;
    private final CacheConfig dictionaryCache;
    private final CacheConfig dictionaryWordCache;

    public DictionaryConfig() {
        dbConfig = new DictionaryDbConfig();
        dictionaryCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        dictionaryWordCache = CacheConfig.builder()
                .maximumSize(10000L)
                .expireAfterWrite(StroomDuration.ofHours(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public DictionaryConfig(@JsonProperty("db") final DictionaryDbConfig dbConfig,
                            @JsonProperty("dictionaryCache") final CacheConfig dictionaryCache,
                            @JsonProperty("dictionaryWordCache") final CacheConfig dictionaryWordCache) {
        this.dbConfig = dbConfig;
        this.dictionaryCache = dictionaryCache;
        this.dictionaryWordCache = dictionaryWordCache;
    }

    @Override
    @JsonProperty("db")
    public DictionaryDbConfig getDbConfig() {
        return dbConfig;
    }

    public CacheConfig getDictionaryCache() {
        return dictionaryCache;
    }

    public CacheConfig getDictionaryWordCache() {
        return dictionaryWordCache;
    }

    @BootStrapConfig
    public static class DictionaryDbConfig extends AbstractDbConfig {

        public DictionaryDbConfig() {
            super();
        }

        @JsonCreator
        public DictionaryDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
