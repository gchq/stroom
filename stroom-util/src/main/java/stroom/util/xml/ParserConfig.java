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

package stroom.util.xml;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ParserConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig cacheConfig;

    private final boolean secureProcessing;

    public ParserConfig() {
        cacheConfig = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();

        secureProcessing = true;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ParserConfig(@JsonProperty("cache") final CacheConfig cacheConfig,
                        @JsonProperty("secureProcessing") final boolean secureProcessing) {
        this.cacheConfig = cacheConfig;
        this.secureProcessing = secureProcessing;
    }

    @JsonProperty("cache")
    @JsonPropertyDescription("The cache config for the parser pool.")
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("Instructs the implementation to process XML securely. This may set limits on XML " +
            "constructs to avoid conditions such as denial of service attacks.")
    public boolean isSecureProcessing() {
        return secureProcessing;
    }

    @Override
    public String toString() {
        return "ParserConfig{" +
                "secureProcessing=" + secureProcessing +
                '}';
    }
}
