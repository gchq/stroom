package stroom.security.impl;

import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class AuthorisationConfig extends AbstractConfig implements HasDbConfig {
    private CacheConfig userGroupsCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(30))
            .build();
    private CacheConfig userAppPermissionsCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(30))
            .build();
    private CacheConfig userCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(30))
            .build();
    private CacheConfig userDocumentPermissionsCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();
    private DbConfig dbConfig = new DbConfig();

    public CacheConfig getUserGroupsCache() {
        return userGroupsCache;
    }

    public void setUserGroupsCache(final CacheConfig userGroupsCache) {
        this.userGroupsCache = userGroupsCache;
    }

    public CacheConfig getUserAppPermissionsCache() {
        return userAppPermissionsCache;
    }

    public void setUserAppPermissionsCache(final CacheConfig userAppPermissionsCache) {
        this.userAppPermissionsCache = userAppPermissionsCache;
    }

    public CacheConfig getUserCache() {
        return userCache;
    }

    public CacheConfig getUserDocumentPermissionsCache() {
        return userDocumentPermissionsCache;
    }

    public void setUserDocumentPermissionsCache(final CacheConfig userDocumentPermissionsCache) {
        this.userDocumentPermissionsCache = userDocumentPermissionsCache;
    }

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }
}
