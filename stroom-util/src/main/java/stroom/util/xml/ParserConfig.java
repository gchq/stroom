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

import java.util.Objects;


@JsonPropertyOrder(alphabetic = true)
public class ParserConfig extends AbstractConfig implements IsStroomConfig {

    private static final boolean DEFAULT_SECURE_PROCESSING = true;
    private static final boolean DEFAULT_DISABLE_EXTERNAL_ENTITIES = true;

    private final CacheConfig cacheConfig;

    private final boolean secureProcessing;
    private final boolean disableExternalEntities;

    public ParserConfig() {
        cacheConfig = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();

        secureProcessing = DEFAULT_SECURE_PROCESSING;
        disableExternalEntities = DEFAULT_DISABLE_EXTERNAL_ENTITIES;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ParserConfig(@JsonProperty("cache") final CacheConfig cacheConfig,
                        @JsonProperty("secureProcessing") final Boolean secureProcessing,
                        @JsonProperty("disableExternalEntities") final Boolean disableExternalEntities) {
        this.cacheConfig = cacheConfig;
        this.secureProcessing = Objects.requireNonNullElse(secureProcessing, DEFAULT_SECURE_PROCESSING);
        this.disableExternalEntities = Objects.requireNonNullElse(
                disableExternalEntities, DEFAULT_DISABLE_EXTERNAL_ENTITIES);
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

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("When true (the default), XML parsing disallows DOCTYPE declarations and the " +
            "resolution of external general/parameter entities and external DTDs, to prevent XML External " +
            "Entity (XXE) attacks (server-side file disclosure, SSRF and entity-expansion denial of service). " +
            "Set this to false only if a data feed or pipeline legitimately requires DOCTYPE or external " +
            "entities; note that doing so re-exposes the XXE risk. Stroom's internal XML fragment parser is " +
            "unaffected either way.")
    public boolean isDisableExternalEntities() {
        return disableExternalEntities;
    }

    @Override
    public String toString() {
        return "ParserConfig{" +
                "secureProcessing=" + secureProcessing +
                ", disableExternalEntities=" + disableExternalEntities +
                '}';
    }
}
