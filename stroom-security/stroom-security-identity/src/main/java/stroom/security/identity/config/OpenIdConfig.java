/*
 * Copyright 2020 Crown Copyright
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

package stroom.security.identity.config;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class OpenIdConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_ACCESS_CODE_CACHE = "accessCodeCache";

    private final CacheConfig accessCodeCache;

    public OpenIdConfig() {
        accessCodeCache = CacheConfig.builder()
                .maximumSize(1_000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @JsonCreator
    public OpenIdConfig(@JsonProperty(PROP_NAME_ACCESS_CODE_CACHE) final CacheConfig accessCodeCache) {
        this.accessCodeCache = accessCodeCache;
    }

    @JsonProperty(PROP_NAME_ACCESS_CODE_CACHE)
    public CacheConfig getAccessCodeCache() {
        return accessCodeCache;
    }

    @Override
    public String toString() {
        return "OpenIdConfig{" +
               "accessCodeCache=" + accessCodeCache +
               '}';
    }
}
