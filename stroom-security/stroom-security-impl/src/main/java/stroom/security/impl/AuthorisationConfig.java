package stroom.security.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

public class AuthorisationConfig extends AbstractConfig implements HasDbConfig {

    private final CacheConfig userGroupsCache;
    private final CacheConfig userAppPermissionsCache;
    private final CacheConfig userCache;
    private final CacheConfig userDocumentPermissionsCache;
    private final AuthorisationDbConfig dbConfig;

    public AuthorisationConfig() {

        userGroupsCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(30))
                .build();
        userAppPermissionsCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(30))
                .build();
        userCache = CacheConfig.builder()
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
            @JsonProperty("userGroupsCache") final CacheConfig userGroupsCache,
            @JsonProperty("userAppPermissionsCache") final CacheConfig userAppPermissionsCache,
            @JsonProperty("userCache") final CacheConfig userCache,
            @JsonProperty("userDocumentPermissionsCache") final CacheConfig userDocumentPermissionsCache,
            @JsonProperty("db") final AuthorisationDbConfig dbConfig) {

        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.userCache = userCache;
        this.userDocumentPermissionsCache = userDocumentPermissionsCache;
        this.dbConfig = dbConfig;
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

    public CacheConfig getUserDocumentPermissionsCache() {
        return userDocumentPermissionsCache;
    }

    @Override
    @JsonProperty("db")
    public AuthorisationDbConfig getDbConfig() {
        return dbConfig;
    }

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
