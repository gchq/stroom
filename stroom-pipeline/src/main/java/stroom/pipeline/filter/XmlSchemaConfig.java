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

package stroom.pipeline.filter;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class XmlSchemaConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig cacheConfig;

    public XmlSchemaConfig() {
        cacheConfig = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @JsonCreator
    public XmlSchemaConfig(@JsonProperty("cache") final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    @JsonProperty("cache")
    @JsonPropertyDescription("The cache config for the XML schema pool.")
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    @Override
    public String toString() {
        return "XmlSchemaConfig{" +
                "cacheConfig=" + cacheConfig +
                '}';
    }
}
