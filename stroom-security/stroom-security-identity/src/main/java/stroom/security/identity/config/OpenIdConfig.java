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
    public static final String PROP_NAME_REFRESH_TOKEN_CACHE = "refreshTokenCache";

    private final CacheConfig accessCodeCache;
    private final CacheConfig refreshTokenCache;

    public OpenIdConfig() {
        accessCodeCache = CacheConfig.builder()
                .maximumSize(1_000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();

        refreshTokenCache = CacheConfig.builder()
                .maximumSize(10_000L)
                .expireAfterAccess(StroomDuration.ofDays(1))
                .build();
    }

    @JsonCreator
    public OpenIdConfig(@JsonProperty(PROP_NAME_ACCESS_CODE_CACHE) final CacheConfig accessCodeCache,
                        @JsonProperty(PROP_NAME_REFRESH_TOKEN_CACHE) final CacheConfig refreshTokenCache) {
        this.accessCodeCache = accessCodeCache;
        this.refreshTokenCache = refreshTokenCache;
    }

    @JsonProperty(PROP_NAME_ACCESS_CODE_CACHE)
    public CacheConfig getAccessCodeCache() {
        return accessCodeCache;
    }

    @JsonProperty(PROP_NAME_REFRESH_TOKEN_CACHE)
    public CacheConfig getRefreshTokenCache() {
        return refreshTokenCache;
    }

    @Override
    public String toString() {
        return "OpenIdConfig{" +
                "accessCodeCache=" + accessCodeCache +
                '}';
    }
}
