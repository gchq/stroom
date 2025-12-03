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

package stroom.security.impl;

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
public class AuthorisationConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final CacheConfig appPermissionIdCache;
    private final CacheConfig docTypeIdCache;

    private final CacheConfig userGroupsCache;
    private final CacheConfig userAppPermissionsCache;
    private final CacheConfig userCache;
    private final CacheConfig userByUuidCache;
    private final CacheConfig userInfoByUuidCache;
    private final CacheConfig userDocumentPermissionsCache;
    private final AuthorisationDbConfig dbConfig;

    public AuthorisationConfig() {
        appPermissionIdCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(30))
                .build();
        docTypeIdCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(30))
                .build();

        userGroupsCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(30))
                .build();
        userAppPermissionsCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(30))
                .build();
        // User is pretty much immutable apart from the displayName/fullName but any change to
        // this, triggers an entity event to evict the item from the cache, so expireAfterAccess
        // is ok.
        userCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(30))
                .build();
        userByUuidCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(30))
                .build();
        userInfoByUuidCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(30))
                .build();
        userDocumentPermissionsCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        dbConfig = new AuthorisationDbConfig();
    }

    @JsonCreator
    public AuthorisationConfig(
            @JsonProperty("appPermissionIdCache") final CacheConfig appPermissionIdCache,
            @JsonProperty("docTypeIdCache") final CacheConfig docTypeIdCache,
            @JsonProperty("userGroupsCache") final CacheConfig userGroupsCache,
            @JsonProperty("userAppPermissionsCache") final CacheConfig userAppPermissionsCache,
            @JsonProperty("userCache") final CacheConfig userCache,
            @JsonProperty("userByUuidCache") final CacheConfig userByUuidCache,
            @JsonProperty("userInfoByUuidCache") final CacheConfig userInfoByUuidCache,
            @JsonProperty("userDocumentPermissionsCache") final CacheConfig userDocumentPermissionsCache,
            @JsonProperty("db") final AuthorisationDbConfig dbConfig) {
        this.appPermissionIdCache = appPermissionIdCache;
        this.docTypeIdCache = docTypeIdCache;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.userCache = userCache;
        this.userByUuidCache = userByUuidCache;
        this.userInfoByUuidCache = userInfoByUuidCache;
        this.userDocumentPermissionsCache = userDocumentPermissionsCache;
        this.dbConfig = dbConfig;
    }

    public CacheConfig getAppPermissionIdCache() {
        return appPermissionIdCache;
    }

    public CacheConfig getDocTypeIdCache() {
        return docTypeIdCache;
    }

    public CacheConfig getUserGroupsCache() {
        return userGroupsCache;
    }

    public CacheConfig getUserAppPermissionsCache() {
        return userAppPermissionsCache;
    }

    public CacheConfig getUserCache() {
        return userCache;
    }

    public CacheConfig getUserByUuidCache() {
        return userByUuidCache;
    }

    public CacheConfig getUserInfoByUuidCache() {
        return userByUuidCache;
    }

    public CacheConfig getUserDocumentPermissionsCache() {
        return userDocumentPermissionsCache;
    }

    @Override
    @JsonProperty("db")
    public AuthorisationDbConfig getDbConfig() {
        return dbConfig;
    }

    @Override
    public String toString() {
        return "AuthorisationConfig{" +
               "appPermissionIdCache=" + appPermissionIdCache +
               ", docTypeIdCache=" + docTypeIdCache +
               ", userGroupsCache=" + userGroupsCache +
               ", userAppPermissionsCache=" + userAppPermissionsCache +
               ", userCache=" + userCache +
               ", userByUuidCache=" + userByUuidCache +
               ", userDocumentPermissionsCache=" + userDocumentPermissionsCache +
               ", dbConfig=" + dbConfig +
               '}';
    }


// --------------------------------------------------------------------------------


    @BootStrapConfig
    public static class AuthorisationDbConfig extends AbstractDbConfig {

        public AuthorisationDbConfig() {
            super();
        }

        @JsonCreator
        public AuthorisationDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
