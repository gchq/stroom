package stroom.security.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class AuthorisationConfig implements IsConfig {
    private CacheConfig userGroupsCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
    private CacheConfig userAppPermissionsCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
    private CacheConfig userCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
    private CacheConfig userDocumentPermissionsCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private CacheConfig documentPermissionCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

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

    public CacheConfig getDocumentPermissionCache() {
        return documentPermissionCache;
    }

    public void setDocumentPermissionCache(final CacheConfig documentPermissionCache) {
        this.documentPermissionCache = documentPermissionCache;
    }
}
