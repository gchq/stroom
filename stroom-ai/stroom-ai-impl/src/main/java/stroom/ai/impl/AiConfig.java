/*
 * Copyright 2018 Crown Copyright
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

package stroom.ai.impl;

import stroom.ai.impl.db.AiDbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class AiConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final AiDbConfig dbConfig;
    private final CacheConfig chatResponseCache;

    public AiConfig() {
        dbConfig = new AiDbConfig();
        chatResponseCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AiConfig(@JsonProperty("db") final AiDbConfig dbConfig,
                    @JsonProperty("chatResponseCache") final CacheConfig chatResponseCache) {
        this.dbConfig = dbConfig;
        this.chatResponseCache = chatResponseCache;
    }

    @Override
    @JsonProperty("db")
    public AiDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonPropertyDescription("Caches the answers given by the ai() XSLT and StroomQL functions, keyed on the " +
                             "model, system prompt and message, so that repeated identical questions do not " +
                             "each result in a call to the model. Set maximumSize to 0 to ask the model every " +
                             "time.")
    @JsonProperty("chatResponseCache")
    public CacheConfig getChatResponseCache() {
        return chatResponseCache;
    }

    @Override
    public String toString() {
        return "AiConfig{" +
               "dbConfig=" + dbConfig +
               ", chatResponseCache=" + chatResponseCache +
               '}';
    }
}
