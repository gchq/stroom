/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.server;

import stroom.cache.AbstractCacheBean;
import stroom.security.shared.DocumentPermissionKey;
import stroom.security.shared.DocumentPermissionKeySet;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;
import net.sf.ehcache.CacheManager;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
public class UserPermissionsCache extends AbstractCacheBean<UserRef, UserPermissions> {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final DocumentPermissionService documentPermissionService;
    private final UserAppPermissionService userAppPermissionService;

    @Inject
    public UserPermissionsCache(final CacheManager cacheManager,
            final DocumentPermissionService documentPermissionService,
                                final UserAppPermissionService userAppPermissionService) {
        super(cacheManager, "User Permissions Cache", MAX_CACHE_ENTRIES);
        this.documentPermissionService = documentPermissionService;
        this.userAppPermissionService = userAppPermissionService;
        setMaxIdleTime(30, TimeUnit.MINUTES);
        setMaxLiveTime(30, TimeUnit.MINUTES);
    }

    @Override
    protected UserPermissions create(final UserRef user) {
        final UserPermissions userPermissions = new UserPermissions();

        if (user != null) {
            final DocumentPermissionKeySet documentPermissionKeySet = documentPermissionService
                    .getPermissionKeySetForUser(user);
            final UserAppPermissions userAppPermissions = userAppPermissionService
                    .getPermissionsForUser(user);

            // Add document permissions.
            for (final DocumentPermissionKey documentPermissionKey : documentPermissionKeySet) {
                userPermissions.addDocumentPermission(documentPermissionKey);
            }

            // Add feature permissions.
            for (final String permission : userAppPermissions.getUserPermissons()) {
                userPermissions.addAppPermission(permission);
            }
        }

        return userPermissions;
    }
}
